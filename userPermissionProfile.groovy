import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRUserNode
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.registries.*;
import org.jahia.api.*;
import java.io.*;

import javax.jcr.*
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;


final def log = log;

def resource = "/sites/sitekey/"
def username = "username"
def siteKeyForUser = null  //if user is a siteuser, set a sitekey
def permissions = true   //false will not print permissions
def mode = "default"


final JCRCallback callBack = new JCRCallback<Object>() {
    public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
        try {
		log.info ("Roles/Permission check for Resource " + resource);
		JCRNodeWrapper node = session.getNode(resource);
		JCRUserNode user = ServicesRegistry.getInstance().getJahiaUserManagerService().lookupUser(username, (String)siteKeyForUser);
		JahiaGroupManagerService groupManagerService = JahiaGroupManagerService.getInstance();
		List<String> groupsPathes = groupManagerService.getUserMembership(username, siteKeyForUser);
		StringTokenizer token = new StringTokenizer(resource, "/");
		token.nextToken(); // sites
		String siteKeyOfResource = token.nextToken();
		
		log.info ("User '" + username + "' has follow global group memberships: " + groupsPathes);
		
		List<String> groups = new ArrayList<String>();
		for (String groupPath : groupsPathes) {
			if (groupPath.startsWith("/sites")) {
				if (groupPath.startsWith("/sites/" + siteKeyOfResource + "/")) {
					groups.add(groupPath.substring(groupPath.lastIndexOf("/") + 1));
				}
			} else {
				groups.add(groupPath.substring(groupPath.lastIndexOf("/") + 1));
			}
			
		}
		
		log.info ("User '" + username + "' has follow resource related group memberships: " + groups);
		
		Map<String, String> userroles = new HashMap<String, String>();
		Map<String, String> denyuserroles = new HashMap<String, String>();
		
		while (!node.getPath().equals("/sites")) {
			if (node.hasNode("j:acl")) {
			  	JCRNodeWrapper acls = node.getNode("j:acl");
			  	JCRNodeIteratorWrapper aclsit = acls.getNodes();
			  	while (aclsit.hasNext()) {
			  		JCRNodeWrapper ace = (JCRNodeWrapper)aclsit.next();
			  		if (ace.isNodeType("jnt:ace") && !ace.isNodeType("jnt:externalAce") && ace.getPropertyAsString("j:aceType").equals("GRANT")) {
			  			//checkuser
			  			if (ace.getPropertyAsString("j:principal").equals("u:" + username)) {
			  				JCRValueWrapper[] roles = ace.getProperty("j:roles").getValues();
			  				for (JCRValueWrapper role : roles) {
			  					if (!userroles.containsKey(role.getString()) && !denyuserroles.containsKey(role.getString())) {
			  						userroles.put(role.getString(), role.getString());
			  					}
			  				}
			  			}
			  			//check groups
			  			for (String group : groups) {
				  			if (ace.getPropertyAsString("j:principal").equals("g:" + group)) {
				  				JCRValueWrapper[] roles = ace.getProperty("j:roles").getValues();
				  				for (JCRValueWrapper role : roles) {
				  					if (!userroles.containsKey(role.getString())&& !denyuserroles.containsKey(role.getString())) {
				  						userroles.put(role.getString(), role.getString());
				  					}
				  				}
				  			}
				  							  				
			  			}
			  		} else if (ace.isNodeType("jnt:ace") && !ace.isNodeType("jnt:externalAce") && ace.getPropertyAsString("j:aceType").equals("DENY")) {
			  			//deny role
			  			if (ace.getPropertyAsString("j:principal").equals("u:" + username)) {
			  				JCRValueWrapper[] roles = ace.getProperty("j:roles").getValues();
			  				for (JCRValueWrapper role : roles) {
			  					if (!userroles.containsKey(role.getString())) {
			  						denyuserroles.put(role.getString(), role.getString());
			  					}
			  				}
			  			}
			  			//check groups
			  			for (String group : groups) {
				  			if (ace.getPropertyAsString("j:principal").equals("g:" + group)) {
				  				JCRValueWrapper[] roles = ace.getProperty("j:roles").getValues();
				  				for (JCRValueWrapper role : roles) {
				  					if (!userroles.containsKey(role.getString())) {
				  						denyuserroles.put(role.getString(), role.getString());
				  					}
				  				}
				  			}
				  							  				
			  			}			  			
			  		}

			  	}

			}
		    //continue on Parent node
			node = node.getParent();
		}
		log.info("User roles for node:");
		log.info(userroles.keySet().toString());
		
		if (permissions) {
			Map<String, JCRNodeWrapper> availableRoles = new HashMap<String, JCRNodeWrapper>();
			javax.jcr.NodeIterator ni = session.getWorkspace().getQueryManager().createQuery(
					"select * from [" + org.jahia.api.Constants.JAHIANT_ROLE + "] as r where isdescendantnode(r,['/roles'])",
					Query.JCR_SQL2).execute().getNodes();
			while (ni.hasNext()) {
				JCRNodeWrapper role = (JCRNodeWrapper) ni.nextNode();
				availableRoles.put(role.getName(), role);
			}
			Map<String, JCRNodeWrapper> availablePermissions = new HashMap<String, JCRNodeWrapper>();
			javax.jcr.NodeIterator per = session.getWorkspace().getQueryManager().createQuery(
					"select * from [" + org.jahia.api.Constants.JAHIANT_PERMISSION + "]",
					Query.JCR_SQL2).execute().getNodes();
			while (per.hasNext()) {
				JCRNodeWrapper perm = (JCRNodeWrapper) per.nextNode();
				availablePermissions.put(perm.getName(), perm);
			}			
			for (String role : userroles.keySet()) {
				List<String> permissionNames = getAllPermissionsFromRole(availableRoles.get(role), availablePermissions);
				
				log.info ("Role '" + role + "' has follow permissions:");
				log.info(permissionNames.toString());
			}
		}
		
        } catch (Exception ex) {
            log.error("Error in Script ", ex)
        } finally {
           
        }
        return null;
    }
};
JCRTemplate.getInstance().doExecuteWithSystemSession(null, mode, callBack);


	public List<String> getAllPermissionsFromRole (JCRNodeWrapper role, Map<String, JCRNodeWrapper> allPermissions) throws RepositoryException {
		
		List<String> resultPermissions = new ArrayList<String>();
		JCRValueWrapper[] permissionNames = role.getProperty("j:permissionNames").getValues();
		for (JCRValueWrapper permissionName : permissionNames) {
			resultPermissions.add(permissionName.getString());
			resultPermissions.addAll(getSubPermissionNames(allPermissions.get(permissionName.getString())));
		}

		//externalPermisisons
		if (role.hasNodes()) {
			 JCRNodeIteratorWrapper nodes = role.getNodes();
			 while (nodes.hasNext()) {
			  	JCRNodeWrapper node = (JCRNodeWrapper)nodes.next();
			  	if (node.isNodeType("jnt:externalPermissions")) {
			  		JCRValueWrapper[] permissionNames1 = node.getProperty("j:permissionNames").getValues();
					for (JCRValueWrapper permissionName : permissionNames1) {
						resultPermissions.add(permissionName.getString());
						resultPermissions.addAll(getSubPermissionNames(allPermissions.get(permissionName.getString())));
					}
			  	}
			 }
		}

		return resultPermissions;
	}
	
 public List<String> getSubPermissionNames(JCRNodeWrapper permission) throws RepositoryException {
 
	 List<String> resultPermissions = new ArrayList<String>();
	 if (permission == null) {
	      return resultPermissions;
	 }
	 
	 if (permission.hasNodes()) {
		 JCRNodeIteratorWrapper sPerms = permission.getNodes();
		  	while (sPerms.hasNext()) {
		  		JCRNodeWrapper sPerm = (JCRNodeWrapper)sPerms.next();
		  		resultPermissions.add(sPerm.getName());
		  		resultPermissions.addAll(getSubPermissionNames(sPerm));
		  	}
	 }
	 
	 return resultPermissions;
 }
