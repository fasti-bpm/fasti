<%--
  Sends requests for the former application path /camunda to the same path
  below /fasti, keeping the query string. GET and HEAD are answered with 301,
  all other methods with 308, so that clients repeat them with the same method
  and body. Not cached, so that restoring /camunda takes effect at once.
--%><%@ page session="false" trimDirectiveWhitespaces="true" %><%
  String uri = request.getRequestURI();
  String rest = uri.substring(request.getContextPath().length());
  // a single leading slash: the location must stay on this host
  int start = 0;
  while (start < rest.length() && (rest.charAt(start) == '/' || rest.charAt(start) == '\\')) {
    start++;
  }
  if (start > 1) {
    rest = "/" + rest.substring(start);
  }
  String location = "/fasti" + rest;
  String query = request.getQueryString();
  if (query != null && !query.isEmpty()) {
    location += "?" + query;
  }
  String method = request.getMethod();
  boolean safe = "GET".equals(method) || "HEAD".equals(method);
  response.setStatus(safe ? 301 : 308);
  response.setHeader("Location", location);
  response.setHeader("Cache-Control", "no-store");
  response.setContentLength(0);
%>
