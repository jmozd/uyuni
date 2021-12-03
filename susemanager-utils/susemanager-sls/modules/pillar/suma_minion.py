# -*- coding: utf-8 -*-
'''
Retrieve SUSE Manager pillar data for a minion_id.
- Adds generated and static SUSE Manager pillar data.
- Adds formula pillar data.

.. code-block:: yaml

    ext_pillar:
      - suma_minion: True

'''

# Import python libs
from __future__ import absolute_import
from enum import Enum
from contextlib import contextmanager
import os
import logging
import yaml
import json
import salt.utils.dictupdate
import salt.utils.stringutils

try:
    import psycopg2
    HAS_POSTGRES = True
except ImportError:
    HAS_POSTGRES = False

# SUSE Manager static pillar paths:
MANAGER_STATIC_PILLAR_DATA_PATH = '/usr/share/susemanager/pillar_data'
MANAGER_PILLAR_DATA_PATH = '/srv/susemanager/pillar_data'

# SUSE Manager formulas paths:
MANAGER_FORMULAS_METADATA_MANAGER_PATH = '/usr/share/susemanager/formulas/metadata'
MANAGER_FORMULAS_METADATA_STANDALONE_PATH = '/usr/share/salt-formulas/metadata'
CUSTOM_FORMULAS_METADATA_PATH = '/srv/formula_metadata'
FORMULAS_DATA_PATH = '/srv/susemanager/formula_data'
FORMULA_ORDER_FILE = FORMULAS_DATA_PATH + '/formula_order.json'
FORMULA_PREFIX = 'formula-'

# OS images path:
IMAGES_DATA_PATH = os.path.join(MANAGER_PILLAR_DATA_PATH, 'images')

# SUSE Manager static pillar data.
MANAGER_STATIC_PILLAR = [
    'gpgkeys'
]

MANAGER_GLOBAL_PILLAR = [
    'mgr_conf'
]

MINION_PILLAR_FILES_PREFIX = "pillar_{minion_id}"
MINION_PILLAR_FILES_SUFFIXES = [".yml", "_group_memberships.yml", "_virtualization.yml", "_custom_info.yml"]

CONFIG_FILE = '/etc/rhn/rhn.conf'

formulas_metadata_cache = dict()

# Fomula group subtypes
class EditGroupSubtype(Enum):
    PRIMITIVE_LIST = "PRIMITIVE_LIST"
    PRIMITIVE_DICTIONARY = "PRIMITIVE_DICTIONARY"
    LIST_OF_DICTIONARIES = "LIST_OF_DICTIONARIES"
    DICTIONARY_OF_DICTIONARIES = "DICTIONARY_OF_DICTIONARIES"

# Set up logging
log = logging.getLogger(__name__)


def __virtual__():
    '''
    Ensure the pillar module name.
    '''
    return HAS_POSTGRES

@contextmanager
def _get_cursor():
    defaults = {
        'host': 'localhost',
        'user': 'spacewalk',
        'pass': 'spacewalk',
        'db': 'susemanager',
        'port': 5432,
    }
    opts = __opts__.get("postgres", {})
    options = {}
    for attr, default in defaults.items():
        options[attr] = opts.get(attr, default)

    cnx = psycopg2.connect(
            host=options['host'],
            user=options['user'],
            password=options['pass'],
            dbname=options['db'],
            port=options['port'])
    cursor = cnx.cursor()
    try:
        log.debug("Connected to DB")
        yield cursor
    except psycopg2.DatabaseError as err:
        log.error("Error in database pillar: %s", err.args)
    finally:
        cnx.close()


def ext_pillar(minion_id, pillar, *args):
    '''
    Find SUMA-related pillars for the registered minions and return the data.
    '''

    log.debug('Getting pillar data for the minion "{0}"'.format(minion_id))
    ret = {}

    # Load the pillar from the legacy files
    ret = load_legacy_pillars(minion_id, ret)

    # Load the global pillar from DB
    with _get_cursor() as cursor:
        ret = load_global_pillars(cursor, ret)
        ret = load_org_pillars(minion_id, cursor, ret)
        group_formulas, ret = load_group_pillars(minion_id, cursor, ret)
        system_formulas, ret= load_system_pillars(minion_id, cursor, ret)

    # Including formulas into pillar data
    try:
        ret.update(formula_pillars(system_formulas, group_formulas, ret))
    except Exception as error:
        log.error('Error accessing formula pillar data: %s', error)

    # Including images pillar
    try:
        ret.update(image_pillars(minion_id, ret.get("group_ids", []), ret.get("org_id", 1)))
    except Exception as error:
        log.error('Error accessing image pillar data: {}'.format(str(error)))

    return ret


def get_formula_order(pillar):
    '''
    Get the formula order either from the legacy file or from the pillar
    '''
    if 'formula_order' in pillar:
        return pillar.pop('formula_order')

    if os.path.exists(FORMULA_ORDER_FILE):
        with open(FORMULA_ORDER_FILE) as ofile:
            return json.load(ofile)
    return []


def load_global_pillars(cursor, pillar):
    '''
    Load the global pillar from the database
    '''
    log.debug('Loading global pillars from db')
    # Query for global pillar and extract the formula order
    cursor.execute('''
            SELECT p.pillar
            FROM susesaltpillar AS p
            WHERE p.server_id is NULL AND p.group_id is NULL AND p.org_id is NULL;''')
    for row in cursor.fetchall():
        pillar = salt.utils.dictupdate.merge(pillar, row[0], strategy='recurse')
    return pillar


def load_org_pillars(minion_id, cursor, pillar):
    '''
    Load the org pillar from the database
    '''
    cursor.execute('''
            SELECT p.pillar
            FROM susesaltpillar AS p,
                 suseminioninfo AS m
            WHERE m.minion_id = %s
              AND p.org_id = (SELECT s.org_id FROM rhnServer AS s WHERE s.id = m.server_id);''', (minion_id,))
    for row in cursor.fetchall():
        pillar = salt.utils.dictupdate.merge(pillar, row[0], strategy='recurse')
    return pillar


def load_group_pillars(minion_id, cursor, pillar):
    '''
    Load the group pillars from the DB and extract the formulas from it
    '''
    groups_query = '''
        SELECT p.category, p.pillar
        FROM susesaltpillar AS p,
             suseminioninfo AS m
        WHERE m.minion_id = %s
          AND p.group_id IN (
            SELECT g.server_group_id
            FROM rhnServerGroupMembers AS g
            WHERE g.server_id = m.server_id
          );
    '''
    cursor.execute(groups_query, (minion_id,));
    group_formulas = {}
    for row in cursor.fetchall():
        if row[0].startswith(FORMULA_PREFIX):
            # Handle formulas separately
            group_formulas[row[0][len(FORMULA_PREFIX):]] = row[1]
        else:
            pillar = salt.utils.dictupdate.merge(pillar, row[1], strategy='recurse')

    # Now look for the legacy files in case the formulas haven't been migrated yet
    data = load_formulas_from_file("group_formulas.json")
    for group in pillar.get("group_ids", []):
        for formula in data.get(str(group), []):
            formula_utf8 = salt.utils.stringutils.to_str(formula)
            group_filename = os.path.join(FORMULAS_DATA_PATH, "group_pillar",
                    "{id}_{name}.json".format(id=group, name=formula_utf8))
            formula_data = load_formula_data(group_filename)
            group_formulas[formula_utf8] = formula_data

    return (group_formulas, pillar)


def load_system_pillars(minion_id, cursor, pillar):
    '''
    Load the system pillars from the DB and extract the formulas from it
    '''
    minion_query = '''
        SELECT p.category, p.pillar
        FROM susesaltpillar AS p,
             suseminioninfo AS m
        WHERE m.minion_id = %s
          AND m.server_id = p.server_id;'''
    cursor.execute(minion_query, (minion_id,))
    server_formulas = {}
    for row in cursor.fetchall():
        if row[0].startswith(FORMULA_PREFIX):
            # Handle formulas separately
            server_formulas[row[0][len(FORMULA_PREFIX):]] = row[1]
        else:
            pillar = salt.utils.dictupdate.merge(pillar, row[1], strategy='recurse')

    # Now look for the legacy files in case the formulas haven't been migrated yet
    data = load_formulas_from_file("minion_formulas.json")
    for formula in data.get(str(minion_id), []):
        formula_utf8 = salt.utils.stringutils.to_str(formula)
        system_filename = os.path.join(FORMULAS_DATA_PATH, "pillar",
                "{id}_{name}.json".format(id=minion_id, name=formula_utf8))
        formula_data = load_formula_data(system_filename)
        server_formulas[formula_utf8] = formula_data

    return (server_formulas, pillar)


def load_legacy_pillars(minion_id, pillar):
    # Including SUSE Manager static pillar data
    for static_pillar in MANAGER_STATIC_PILLAR:
        static_pillar_filename = os.path.join(MANAGER_STATIC_PILLAR_DATA_PATH, static_pillar)
        try:
            pillar.update(yaml.load(open('{0}.yml'.format(static_pillar_filename)).read(), Loader=yaml.FullLoader))
        except Exception as exc:
            log.error('Error accessing "{0}": {1}'.format(static_pillar_filename, exc))

    # Including SUSE Manager global pillar data
    for global_pillar in MANAGER_GLOBAL_PILLAR:
        global_pillar_filename = os.path.join(MANAGER_PILLAR_DATA_PATH, global_pillar)
        try:
            # Global pillars may no longer exist once they have been migrated to the database
            if os.path.exists(global_pillar_filename):
                pillar.update(yaml.load(open('{0}.yml'.format(global_pillar_filename)).read(), Loader=yaml.FullLoader))
        except Exception as exc:
            log.error('Error accessing "{0}": {1}'.format(global_pillar_filename, exc))

    # Including generated pillar data for this minion
    minion_pillar_filename_prefix = MINION_PILLAR_FILES_PREFIX.format(minion_id=minion_id)
    for suffix in MINION_PILLAR_FILES_SUFFIXES:
        data_filename = os.path.join(MANAGER_PILLAR_DATA_PATH, minion_pillar_filename_prefix + suffix)
        if os.path.exists(data_filename):
            try:
                pillar = salt.utils.dictupdate.merge(
                        pillar,
                        yaml.load(open(data_filename).read(), Loader=yaml.FullLoader),
                        strategy='recurse')
            except Exception as error:
                log.error('Error accessing "{pillar_file}": {message}'.format(pillar_file=data_filename, message=str(error)))
    return pillar


def load_formulas_from_file(formula_filename):
    formulas = {}
    formula_file = os.path.join(FORMULAS_DATA_PATH, formula_filename)
    if os.path.exists(formula_file):
        try:
            with open(formula_file) as f:
                formulas = json.load(f)
        except Exception as error:
            log.error('Error loading formulas from file: {message}'.format(message=str(error)))
    return formulas


def formula_pillars(system_formulas, group_formulas, all_pillar):
    '''
    Find formula pillars for the minion, merge them and return the data.
    '''
    pillar = {}
    out_formulas = []

    # Loading group formulas
    for formula_name in group_formulas:
        formula_metadata = load_formula_metadata(formula_name)
        if formula_metadata.get("type", "") != "cluster-formula":
            # a minion can be in multiple cluster groups, each group with its own cluster-formulas
            # in such a case we want to merge all values from cluster-formulas
            # the values of the formula will be under different keys, mgr_clusters:cluster1:.., mgr_clusters:cluster2:...
            if formula_name in out_formulas:
                continue # already processed
        out_formulas.append(formula_name)
        pillar = salt.utils.dictupdate.merge(pillar,
                       load_formula_pillar(system_formulas.get(formula_name, {}),
                           group_formulas[formula_name],
                           formula_name,
                           formula_metadata),
                        strategy='recurse')

    # Loading minion formulas
    for formula_name in system_formulas:
        if formula_name in out_formulas:
            continue # already processed
        out_formulas.append(formula_name)
        pillar = salt.utils.dictupdate.merge(pillar,
                load_formula_pillar(system_formulas[formula_name], {}, formula_name), strategy='recurse')

    # Loading the formula order
    order = get_formula_order(all_pillar)
    if order:
        pillar["formulas"] = [formula for formula in order if formula in out_formulas]
    else:
        pillar["formulas"] = out_formulas

    return pillar


def load_formula_data(path):
    '''
    Load the JSON data from the legacy formula file
    '''
    try:
        data = json.load(open(path)) if path is not None and os.path.isfile(path) else {}
    except Exception as error:
        log.error('Error loading data from "{path}": {message}'.format(path=path, message=str(error)))
        return {}
    return data


def load_formula_pillar(system_data, group_data, formula_name, formula_metadata = None):
    '''
    Load the data from a specific formula for a minion in a specific group, merge and return it.
    '''
    layout_filename = os.path.join(MANAGER_FORMULAS_METADATA_STANDALONE_PATH, formula_name, "form.yml")
    if not os.path.isfile(layout_filename):
        layout_filename = os.path.join(MANAGER_FORMULAS_METADATA_MANAGER_PATH, formula_name, "form.yml")
        if not os.path.isfile(layout_filename):
            layout_filename = os.path.join(CUSTOM_FORMULAS_METADATA_PATH, formula_name, "form.yml")
            if not os.path.isfile(layout_filename):
                log.error('Error loading data for formula "{formula}": No form.yml found'.format(formula=formula_name))
                return {}

    try:
        layout = yaml.load(open(layout_filename).read(), Loader=yaml.FullLoader)
    except Exception as error:
        log.error('Error loading form.yml of formula "{formula}": {message}'.format(formula=formula_name, message=str(error)))
        return {}

    # if group_data starts with mgr_clusters then merge and adjust without the mgr_clusters:<cluster>:settings prefix
    cluster_name = None
    cluster_pillar_key = None
    if formula_metadata and formula_metadata.get("type", "") == "cluster-formula":
        if "cluster_pillar_key" not in formula_metadata:
            log.error("No 'cluster_pillar_key' in metadata of formula {}".format(formula_name))
        else:
            cluster_pillar_key = formula_metadata["cluster_pillar_key"]
            group_data, cluster_name = _pillar_value_by_path(group_data, "mgr_clusters:*:{}".format(cluster_pillar_key))

    merged_data = merge_formula_data(layout, group_data, system_data)
    merged_data = adjust_empty_values(layout, merged_data)

    # put back data under cluster pillar namespace
    if cluster_name:
        merged_data = {"mgr_clusters": {cluster_name: {cluster_pillar_key: merged_data}}}

    return merged_data


def merge_formula_data(layout, group_data, system_data, scope="system"):
    '''
    Merge the group and system formula data, respecting the scope of a value.
    '''
    ret = {}

    for element_name in layout:
        if element_name.startswith("$"):
            continue

        element = layout[element_name]
        if not isinstance(element, dict):
            continue

        element_scope = element.get("$scope", scope)
        value = None

        if element.get("$type", "text") in ["group", "hidden-group", "namespace"]:
            value = merge_formula_data(element, group_data.get(element_name, {}), system_data.get(element_name, {}), element_scope)
        # edit-group is handled as primitive element - use either system_data or group data, no merging
        elif element_scope == "system":
            value = system_data.get(element_name, group_data.get(element_name, element.get("$default", element.get("$placeholder", ""))))
        elif element_scope == "group":
            value = group_data.get(element_name, element.get("$default", element.get("$placeholder", "")))
        elif element_scope == "readonly":
            value = element.get("$default", element.get("$placeholder", ""))

        ret[element_name] = value
    return ret

def adjust_empty_values(layout, data):
    '''
    Adjust empty values in formula data
    '''
    ret = {}

    for element_name in layout:
        if element_name.startswith("$"):
            continue

        element = layout[element_name]
        if not isinstance(element, dict):
            continue

        element_type = element.get("$type", "text")
        value = data.get(element_name, "")

        if element_type in ["group", "hidden-group", "namespace"]:
            value = adjust_empty_values(element, data.get(element_name, {}))
        elif element_type in ["edit-group"]:
            prototype = element.get("$prototype")
            subtype = get_edit_group_subtype(element)
            if subtype is EditGroupSubtype.DICTIONARY_OF_DICTIONARIES:
                value = {}
                if isinstance(data.get(element_name), dict):
                    for key, entry in list(data.get(element_name).items()):
                        proc_entry = adjust_empty_values(prototype, entry)
                        value[key] = proc_entry
            elif subtype is EditGroupSubtype.LIST_OF_DICTIONARIES:
                value = []
                if isinstance(data.get(element_name), list):
                    for entry in data.get(element_name):
                        proc_entry = adjust_empty_values(prototype, entry)
                        value.append(proc_entry)

        if not value and '$ifEmpty' in element:
            value = element.get("$ifEmpty")

        if value or not element.get("$optional"):
            ret[element_name] = value
    return ret

def get_edit_group_subtype(element):
    if element is not None and element.get("$prototype"):
        prototype = element.get("$prototype")
        if prototype.get("$key") is None and prototype.get("$type", "group") != "group":
            return EditGroupSubtype.PRIMITIVE_LIST
        if prototype.get("$key") is not None and prototype.get("$type", "group") != "group":
            return EditGroupSubtype.PRIMITIVE_DICTIONARY
        if prototype.get("$key") is None and prototype.get("$type", "group") == "group":
            return EditGroupSubtype.LIST_OF_DICTIONARIES
        if prototype.get("$key") is not None and prototype.get("$type", "group") == "group":
            return EditGroupSubtype.DICTIONARY_OF_DICTIONARIES
    return None

def image_pillars(minion_id, group_ids, org_id):
    '''
    Load image pillars

    Image pillars are automatically created after image build and are available to all minions
    '''
    ret = {}
    group_dirs = []
    org_dirs = []

    for pillar in os.listdir(IMAGES_DATA_PATH):
        pillar_path = os.path.join(IMAGES_DATA_PATH, pillar)

        # read also pilars from top dir, for backward compatibility
        if os.path.isfile(pillar_path) and pillar.endswith('.sls'):
            try:
                with open(pillar_path) as p:
                    ret = salt.utils.dictupdate.merge(ret, yaml.load(p.read(), Loader=yaml.FullLoader), strategy='recurse')
            except Exception as error:
                log.error('Error loading data for image "{image}": {message}'.format(image=pillar.path(), message=str(error)))

        elif os.path.isdir(pillar_path):
            if pillar.startswith('org') and int(pillar[3:]) == org_id:
                org_dirs.append(pillar_path)
            elif pillar.startswith('group') and int(pillar[5:]) in group_ids:
                group_dirs.append(pillar_path)

    for pillar_dir in org_dirs + group_dirs:
        for pillar in os.listdir(pillar_dir):
            pillar_path = os.path.join(pillar_dir, pillar)
            if os.path.isfile(pillar_path) and pillar.endswith('.sls'):
                try:
                    with open(pillar_path) as p:
                        ret = salt.utils.dictupdate.merge(ret, yaml.load(p.read(), Loader=yaml.FullLoader), strategy='recurse')
                except Exception as error:
                    log.error('Error loading data for image "{image}": {message}'.format(image=pillar.path(), message=str(error)))

    return ret

def load_formula_metadata(formula_name):
    if formula_name in formulas_metadata_cache:
        return formulas_metadata_cache[formula_name]

    metadata_filename = None
    metadata_paths_ordered = [
        os.path.join(MANAGER_FORMULAS_METADATA_STANDALONE_PATH, formula_name, "metadata.yml"),
        os.path.join(MANAGER_FORMULAS_METADATA_MANAGER_PATH, formula_name, "metadata.yml"),
        os.path.join(CUSTOM_FORMULAS_METADATA_PATH, formula_name, "metadata.yml")
    ]

    # Take the first metadata file that exist
    for mpath in metadata_paths_ordered:
        if os.path.isfile(mpath):
            metadata_filename = mpath
            break

    if not metadata_filename:
        log.error('Error loading metadata for formula "{formula}": No metadata.yml found'.format(formula=formula_name))
        return {}
    try:
        metadata = yaml.load(open(metadata_filename).read())
    except Exception as error:
        log.error('Error loading data for formula "{formula}": {message}'.format(formula=formula_name, message=str(error)))
        return {}

    formulas_metadata_cache[formula_name] = metadata
    return metadata

def _pillar_value_by_path(data, path):
    result = data
    first_key = None
    for token in path.split(":"):
        if token == "*":
            first_key = next(iter(result))
            result = result[first_key] if first_key else None
        elif token in result:
            result = result[token]
        else:
            break
    return result, first_key
