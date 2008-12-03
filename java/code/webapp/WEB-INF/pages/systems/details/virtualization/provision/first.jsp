<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://rhn.redhat.com/rhn" prefix="rhn" %>
<%@ taglib uri="http://jakarta.apache.org/struts/tags-bean" prefix="bean" %>
<%@ taglib uri="http://jakarta.apache.org/struts/tags-html" prefix="html" %>

<html:xhtml/>
<html>

  <head>
    <meta http-equiv="Pragma" content="no-cache"/>

    <script language="javascript">

function swapValues(fromCtlId, toCtlId) {
   var fromCtl = document.getElementById(fromCtlId);
   var toCtl = document.getElementById(toCtlId);
   toCtl.value = fromCtl.value;
}

function moveNext() {
   var form = document.getElementById("wizard-form");
   form.submit();
}

function setStep(stepName) {
	var field = document.getElementById("wizard-step");
	field.value = stepName;
}

function setInitialState() {
  var scheduleAsap = document.getElementById("scheduleAsap");
  var scheduleDate = document.getElementById("scheduleDate");
  if(!scheduleAsap.checked && !scheduleDate.checked) {
      scheduleAsap.checked = true;
  }
  var ksid = document.getElementById("ksid");
  var wizform = document.getElementById("wizard-form");
  if(ksid.value == "") {  	
  	for(x = 0; x < wizform.length; x++) {
  	  if(wizform.elements[x].name == "items_selected") {
  	    wizform.elements[x].checked =  true;
  	    ksid.value = wizform.elements[x].value;
  	    break;
      }
    }
  }
  else {
    for(x = 0; x < wizform.length; x++) {
  	  if(wizform.elements[x].name == "items_selected"
  	    && wizform.elements[x].value == ksid.value) {
  	    wizform.elements[x].checked =  true;  	    
  	    break;
      }
    }
  }

}

    </script>
  </head>

  <body onload="setInitialState();">

<html:errors />

<%@ include file="/WEB-INF/pages/common/fragments/systems/system-header.jspf" %>

    <h2><bean:message key="virtualization.provision.first.jsp.header1"/></h2>

    <div class="page-summary">
      <p>
        <bean:message key="virtualization.provision.first.jsp.summary1" arg0="${system.id}" arg1="${system.name}" />
      </p>
    </div>

<h2><bean:message key="virtualization.provision.first.jsp.header2" /></h2>

<div>

<html:form method="POST" action="/systems/details/virtualization/ProvisionVirtualizationWizard.do" styleId="wizard-form">

    <html:hidden property="wizardStep" value="first" styleId="wizard-step" />
    <html:hidden styleId="ksid" property="ksid" />
    <html:hidden property="sid" />
    
	<!--Store form variables obtained from previous page -->
	<html:hidden property="targetProfileType"/>
	<html:hidden property="targetProfile"/>
	<html:hidden property="kernelParams"/>
	
    <table class="details">
      <tr>
        <th><bean:message key="virtualization.provision.first.jsp.guest_name.header"/></th>
        <td>
          <bean:message key="virtualization.provision.first.jsp.guest_name.message1"/>
          <br/>
          <html:text property="guestName" maxlength="256" size="20"/>
          <br/>
          <bean:message key="virtualization.provision.first.jsp.guest_name.tip1" arg0="256"/>
        </td>
      </tr>
      <tr>
        <th><bean:message key="virtualization.provision.first.jsp.memory_allocation.header"/></th>
        <td>
          <bean:message key="virtualization.provision.first.jsp.memory_allocation.message1"/>
          <br/>
          <html:text property="memoryAllocation" maxlength="12" size="6"/>
          <bean:message key="virtualization.provision.first.jsp.memory_allocation.message2" arg0="${system.ramString}" arg1="${system.id}" arg2="${system.name}"/>
          <br/>
          <bean:message key="virtualization.provision.first.jsp.memory_allocation.tip1" arg0="512" arg1="1024"/>
        </td>
      </tr>
      <tr>
        <th><bean:message key="virtualization.provision.first.jsp.virtual_cpus.header"/></th>
        <td>
          <html:text property="virtualCpus" maxlength="2" size="2"/>
          <bean:message key="virtualization.provision.first.jsp.virtual_cpus.message2"/>
          <br/>
          <bean:message key="virtualization.provision.first.jsp.virtual_cpus.tip1" arg0="32"/>
        </td>
      </tr>
      <tr>
        <th><bean:message key="virtualization.provision.first.jsp.storage"/></th>
        <td>
          <div id="localStorageOption">
            <bean:message key="virtualization.provision.first.jsp.storage.local.message1"/>
            <html:text styleId="localStorageMegabytes" property="localStorageMegabytes" maxlength="20" size="6"/>
            <bean:message key="virtualization.provision.first.jsp.storage.local.megabytes"/>
          </div>
          <bean:message key="virtualization.provision.first.jsp.storage.tip1" arg0="2048" arg1="5120"/>
        </td>
      </tr>    
    </table>


    <rhn:list pageList="${requestScope.pageList}" noDataText="virtualization.provision.first.jsp.no.profiles">         
        <rhn:listdisplay renderDisabled="true" paging="true" filterBy="kickstartranges.jsp.profile">
            <rhn:set type="radio" value="${current.id}" />
            <rhn:column header="kickstartranges.jsp.profile">
                ${current.label}
            </rhn:column>

            <rhn:column header="kickstart.channel.label.jsp">
                ${current.channelLabel}
            </rhn:column>      
        </rhn:listdisplay>          
    </rhn:list>
    <c:if test="${requestScope.hasProxies == 'true'}">    
    <h2><img src="/img/rhn-icon-proxy.gif"/><bean:message key="kickstart.schedule.heading.proxy.jsp"/></h2>
    <p>
    <bean:message key="kickstart.schedule.msg.proxy.jsp"/>
    </p>
    <p>
      <option value=""><bean:message key="kickstart.schedule.default.proxy.jsp"/></option>
	  <html:select property="proxyHost">
	      <html:optionsCollection name="proxies"/>
	  </html:select>
    <br />
    <bean:message key="kickstart.schedule.tip.proxy.jsp"/>
    </p>
    </c:if>
    <c:if test="${requestScope.hasProfiles == 'true'}">    
      <h2><img src="/img/rhn-icon-schedule.gif" /><bean:message key="kickstart.schedule.heading3.jsp" /></h2>
      <table width="50%">
        <tr>
          <td><html:radio styleId="scheduleAsap" property="scheduleAsap" value="true"><bean:message key="kickstart.schedule.heading3.option1.jsp" /></html:radio></td>
        </tr>
        <tr>
          <td><html:radio styleId="scheduleDate" property="scheduleAsap" value="false"><bean:message key="kickstart.schedule.heading3.option2.jsp" /></html:radio><br /><br />
              <jsp:include page="/WEB-INF/pages/common/fragments/date-picker.jsp">
                <jsp:param name="widget" value="date"/>
              </jsp:include>
          </td>
        </tr>
      </table>
      <hr />
	  <table width="100%">
	    <tr>
	      <td align="right">
	        <input type="button" value="<bean:message key="kickstart.schedule.button1.jsp" />" onclick="setStep('second');moveNext();" />
            <input type="button" value="<bean:message key="kickstart.schedule.button2.jsp" />" onclick="setStep('third');moveNext();" />
	      </td>
	    </tr>
	  </table>
    </c:if>
    </div>
</html:form>
</div>

</body>
</html>

