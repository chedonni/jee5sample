<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="f" uri="http://java.sun.com/jsf/core"%>
<%@taglib prefix="h" uri="http://java.sun.com/jsf/html"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%> 

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>JSF Page</title>
  </head>
  <body>
  <h1>JSF Page</h1>
  <f:view>
    <h:form>
      <h:outputText value="Name: "/>
      <h:inputText value="#{page.name}"/>
      <h:commandButton value="Submit" action="#{page.hello}"/><br/>
      <br/>
      <h:outputText value="#{page.message}"/><br/>
    </h:form>
  </f:view>
    
  </body>
</html>
