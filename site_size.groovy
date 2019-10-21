import org.jahia.services.content.* 
import javax.jcr.* 
import javax.jcr.query.Query 

def log = log; 

JCRCallback callBack = new JCRCallback<Object>() { 
    public Object doInJCR(JCRSessionWrapper session) throws RepositoryException { 
        def siteFilter = java.util.Arrays.asList();
        def manager = session.getWorkspace().getQueryManager();
        def workspaceName = session.getWorkspace().name;

        def queryStmtSite = "SELECT * FROM [jnt:virtualsite] AS site WHERE ISDESCENDANTNODE('/sites/')";
        def querySite = manager.createQuery(queryStmtSite, Query.JCR_SQL2);
        def nodeIteratorSite = querySite.execute().getNodes();
        while (nodeIteratorSite.hasNext()) {
            def nodeSite = nodeIteratorSite.next();
            def siteName = nodeSite.getPropertyAsString("j:nodename");
            if(siteFilter.isEmpty() || siteName.contains(siteFilter)){

              def queryStmtSize = "SELECT * FROM [jnt:resource] AS info WHERE ISDESCENDANTNODE('/sites/" + siteName + "')";
              def querySize = manager.createQuery(queryStmtSize, Query.JCR_SQL2);
              def nodeIteratorSize = querySize.execute().getNodes();

              def totalSizeInKb = 0;
              def totalNbNodes = 0;

              while (nodeIteratorSize.hasNext()) {
                  def nodeSize = nodeIteratorSize.next();

                  if(nodeSize.hasProperty("jcr:data")){
                      totalSizeInKb = totalSizeInKb + nodeSize.getProperty("jcr:data").getLength();
                    }
                  totalNbNodes++;
                }

              log.info(workspaceName + "," + siteName + "," + (int)totalSizeInKb + "," + totalNbNodes)
            }
        }
    } 
}
log.info("WORKSPACE,SITE_NAME,SIZE_KB,NUMBER_OF_NODES");
JCRTemplate.getInstance().doExecuteWithSystemSession(null, "default", callBack); 
JCRTemplate.getInstance().doExecuteWithSystemSession(null, "live", callBack);