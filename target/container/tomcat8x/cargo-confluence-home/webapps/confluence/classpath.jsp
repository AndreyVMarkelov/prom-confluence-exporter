<%@ page import="java.util.List" %>
<%@ page import="com.atlassian.confluence.util.ClasspathUtils" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.util.Set" %>
<%@ page import="com.atlassian.confluence.util.classpath.DuplicateClassFinder" %>
<%@ page import="com.atlassian.confluence.util.classpath.ClasspathJarDuplicateClassFinder" %>
<%@ page import="com.atlassian.confluence.util.classpath.JarSet" %>
<%--
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
    <% if(!Boolean.getBoolean("com.atlassian.classpathjsp")){ %>
        <head>Not enabled</head>
    </html>
    <%} else { %>


    <head>
        <title>Classpath JSP</title>
    </head>

    <%
        DuplicateClassFinder duplicateClassFinder = new ClasspathJarDuplicateClassFinder(
        ClasspathJarDuplicateClassFinder.EXCLUDE_KNOWN_DUPLICATES);
    %>

    <body>
        <dl>

        <%
            List classLoaders = ClasspathUtils.getThreadContentClassLoaderHierarchy();
            Iterator it = classLoaders.iterator();
            while (it.hasNext())
            {
                ClassLoader classLoader = (ClassLoader) it.next();
                out.println("<dt>ClassLoader: " + classLoader + "</dt>");
                out.println("<dd>");
                out.println("<ol>");
                List listUrls = ClasspathUtils.getClassLoaderClasspath(classLoader);
                for (Iterator iterator = listUrls.iterator(); iterator.hasNext();)
                {
                    URL url = (URL) iterator.next();
                    out.println("<li>" + url + "</li>");
                }
                out.println("</ol>");
                out.println("</dd>");
            }

        %>

        </dl>

        <%
        Set jarSets = duplicateClassFinder.getJarSetsWithCommonClasses();
        if(jarSets.isEmpty())
        {
        %>
                <div class='panelMacro'><table class='tipMacro'>
                    <colgroup><col width='24'><col></colgroup>
                    <tr>
                        <td valign='top'><img src="/images/icons/emoticons/check.png" width="16" height="16" align="absmiddle" alt="" border="0"></td>
                        <td>No duplicate class files found in classpath JARs</td>
                    </tr>
                </table></div>
        <%
        }
        else
        {   %>



         <div class='panelMacro'><table class='warningMacro'>
            <colgroup><col width='24'><col></colgroup>
            <tr>
                <td valign='top'><img src="/images/icons/emoticons/forbidden.png" width="16" height="16" align="absmiddle" alt="" border="0"></td>
                <td>Duplicate class files found in classpath JARs</td>
            </tr>
        </table></div>

        <dl>
            <dt>
            <p>The following JARs have classes in common:</p>
            <ul>

        <%
            for (Iterator jarSetsIterator = jarSets.iterator(); jarSetsIterator.hasNext();)
            {
                JarSet jarSet = (JarSet) jarSetsIterator.next();

                Iterator jars = jarSet.iterator();
                while (jars.hasNext())
                {
                    out.println("<li>" + jars.next() + "</li>");
                }
            %>

            </ul>
            </dt>

            <dd>
            <p>Packages with duplicates:</p>
            <ul>
                <% Iterator packages = duplicateClassFinder.getPackageNames(jarSet).iterator();
                   while(packages.hasNext()){
                     out.println("<li>" + packages.next() + "</li>");
                   }

                    %>
             </ul>
             </dd>
            <%
                    }

                
                }
                
            }%>
            </dl>

        </ul>
    </body>
</html>
