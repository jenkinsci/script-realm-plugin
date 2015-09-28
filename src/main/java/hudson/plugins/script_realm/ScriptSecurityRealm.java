/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.script_realm;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Launcher.LocalLauncher;
import hudson.model.Descriptor;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.tasks.MailAddressResolver;
import hudson.tasks.UserNameResolver;
import hudson.tasks.Mailer;
import hudson.util.QuotedStringTokenizer;
import hudson.util.StreamTaskListener;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationServiceException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.dao.DataAccessException;

/**
 * @author Kohsuke Kawaguchi
 * @author nicobo
 */
public class ScriptSecurityRealm extends AbstractPasswordBasedSecurityRealm {

	private static final Logger LOGGER = Logger.getLogger(ScriptSecurityRealm.class.getName());

	/** Strategy : call the global resolve method (let Jenkins choose) */
	private static final String OPTION_RESOLVER_ANYSTRATEGY = "*";
	/** Strategy : don't resolve */
	private static final String OPTION_RESOLVER_NONESTRATEGY = "";


	public final String commandLine;
	public final String groupsCommandLine;
	public final String groupsDelimiter;
	/** The name of the e-mail resolver to use */
	public final String emailResolver;
	/** The name of the full name resolver to user */
	public final String nameResolver;

	@DataBoundConstructor
	public ScriptSecurityRealm(String commandLine, String groupsCommandLine, String groupsDelimiter, String emailResolver, String nameResolver) {
		this.commandLine = commandLine;
		this.groupsCommandLine = groupsCommandLine;
		if (StringUtils.isBlank(groupsDelimiter)) {
			this.groupsDelimiter = ",";
		} else {
			this.groupsDelimiter = groupsDelimiter;
		}
		this.emailResolver = emailResolver;
		this.nameResolver = nameResolver;
		LOGGER.log(Level.FINE, "Configured with : commandLine=[{0}] groupsCommandLine=[{1}] groupsDelimiter=[{2}] emailResolver=[{3}] nameResolver=[{4}]", new Object[]{commandLine,groupsCommandLine,groupsDelimiter,emailResolver,nameResolver});
	}

	protected UserDetails authenticate(String username, String password) throws AuthenticationException {
		LOGGER.entering(ScriptSecurityRealm.class.getName(), "authenticate", new Object[]{username,password});
		try {
			StringWriter out = new StringWriter();
			LocalLauncher launcher = new LoginScriptLauncher(new StreamTaskListener(out));
			Map<String, String> overrides = new HashMap<String, String>();
			overrides.put("U", username);
			overrides.put("P", password);
			if (isWindows()) {
				overrides.put("SystemRoot", System.getenv("SystemRoot"));
			}
			LOGGER.log(Level.FINE,"Executing command with U=[{0}], P=[{1}]", new Object[]{username,password});
			if (launcher.launch().cmds(QuotedStringTokenizer.tokenize(commandLine)).stdout(new NullOutputStream()).envs(overrides).join() != 0) {
				throw new BadCredentialsException(out.toString());
			}
			GrantedAuthority[] groups = loadGroups(username);

			User user = new User(username, "", true, true, true, true, groups);

			updateUserDetails(username);

			return user;
		} catch (InterruptedException e) {
			throw new AuthenticationServiceException("Interrupted", e);
		} catch (IOException e) {
			throw new AuthenticationServiceException("Failed", e);
		}
	}

	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
		GrantedAuthority[] groups = loadGroups(username);
		return new User(username, "", true, true, true, true, groups);
	}

	@Override
	public GroupDetails loadGroupByGroupname(final String groupname) throws UsernameNotFoundException, DataAccessException {
		return new GroupDetails() {
			public String getName() {
				return groupname;
			}
		};
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<SecurityRealm> {

		public String getDisplayName() {
			return Messages.ScriptSecurityRealm_displayName();
		}

		public String getDefaultEmailResolver() {
			return OPTION_RESOLVER_NONESTRATEGY;
		}

		public String getDefaultNameResolver() {
			return OPTION_RESOLVER_NONESTRATEGY;
		}

		public ListBoxModel doFillEmailResolverItems() {
            ListBoxModel items = new ListBoxModel();
            ExtensionList<MailAddressResolver> mars = MailAddressResolver.all();
            items.add(new Option(Messages.ScriptSecurityRealm_EmailResolver_nonestrategy_label(),OPTION_RESOLVER_NONESTRATEGY));	// This entry will disable resolving if selected
            if ( ! mars.isEmpty() ) {
                items.add(new Option(Messages.ScriptSecurityRealm_EmailResolver_anystrategy_label(),OPTION_RESOLVER_ANYSTRATEGY));	// This entry will use Jenkins's default behavior (calling all found resolvers)
	            // Adds all found e-mail resolvers as options so the user can select one of them
	            for (MailAddressResolver mar : mars) {
	            	// class name is used both as label and value
	                items.add(mar.getClass().getCanonicalName(),mar.getClass().getName());
	            }
            }
            return items;
        }

		public ListBoxModel doFillNameResolverItems() {
            ListBoxModel items = new ListBoxModel();
            ExtensionList<UserNameResolver> unrs = UserNameResolver.all();
            items.add(new Option(Messages.ScriptSecurityRealm_NameResolver_nonestrategy_label(),OPTION_RESOLVER_NONESTRATEGY));	// This entry will disable resolving if selected
            if ( ! unrs.isEmpty() ) {
                items.add(new Option(Messages.ScriptSecurityRealm_NameResolver_anystrategy_label(),OPTION_RESOLVER_ANYSTRATEGY));	// This entry will use Jenkins's default behavior (calling all found resolvers)
	            // Adds all found name resolvers as options so the user can select one of them
	            for (UserNameResolver unr : unrs) {
	            	// class name is used both as label and value
	                items.add(unr.getClass().getCanonicalName(),unr.getClass().getName());
	            }
            }
            return items;
        }
	}

	protected GrantedAuthority[] loadGroups(String username) throws AuthenticationException {
		LOGGER.log(Level.FINE,"Loading groups from command for {0}", new Object[]{username});
		try {
			List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
			authorities.add(AUTHENTICATED_AUTHORITY);
			if (!StringUtils.isBlank(groupsCommandLine)) {
				StringWriter out = new StringWriter();
				LocalLauncher launcher = new LoginScriptLauncher(new StreamTaskListener(out));
				Map<String, String> overrides = new HashMap<String, String>();
				overrides.put("U", username);
				if (isWindows()) {
					overrides.put("SystemRoot", System.getenv("SystemRoot"));
				}
				OutputStream scriptOut = new ByteArrayOutputStream();
				if (launcher.launch().cmds(QuotedStringTokenizer.tokenize(groupsCommandLine)).stdout(scriptOut).envs(overrides).join() == 0) {
					StringTokenizer tokenizer = new StringTokenizer(scriptOut.toString().trim(), groupsDelimiter);
					while (tokenizer.hasMoreTokens()) {
						final String token = tokenizer.nextToken().trim();
						String[] args = new String[] { token, username };
						LOGGER.log(Level.FINE, "granting: {0} to {1}", args);
						authorities.add(new GrantedAuthorityImpl(token));
					}

				} else {
					throw new UsernameNotFoundException(out.toString());
				}
			}
			return authorities.toArray(new GrantedAuthority[0]);
		} catch (InterruptedException e) {
			throw new AuthenticationServiceException("Interrupted", e);
		} catch (IOException e) {
			throw new AuthenticationServiceException("Failed", e);
		}
	}

    /**
     * Updates the display name and e-mail address of the user by calling the chosen resolvers.
     * Most of the code comes from {@link hudson.security.LDAPSecurityRealm}.
     */
    private void updateUserDetails(String username) {

        hudson.model.User user = hudson.model.User.get(username);

        if ( !OPTION_RESOLVER_NONESTRATEGY.equals(nameResolver) ) {
        	String fullname = null;
        	if ( OPTION_RESOLVER_ANYSTRATEGY.equals(nameResolver) ) {
        		LOGGER.log(Level.FINE,"Calling any registered UserNameResolver for {0}",new Object[]{user});
        		fullname = UserNameResolver.resolve(user);
        	} else {
                for (UserNameResolver unr : UserNameResolver.all()) {
                	if ( unr.getClass().getName().equals(nameResolver) ) {
                		LOGGER.log(Level.FINE,"Calling resolver {0} for {1}",new Object[]{nameResolver,user});
                		fullname = unr.findNameFor(user);
                		break;
                	}
            		LOGGER.log(Level.WARNING,"Resolver {0} not found : name not updated",new Object[]{nameResolver});
                }
        	}
        	if ( StringUtils.isNotBlank(fullname) ) {
        		LOGGER.log(Level.FINE,"Setting user's name to {0}",new Object[]{fullname});
        		user.setFullName(fullname);
        	} else {
        		LOGGER.log(Level.FINE,"Null or empty user name : not updating it");
        	}
        } else {
        	LOGGER.log(Level.FINE,"None strategy : not updating the user's name");
        }

        if ( !OPTION_RESOLVER_NONESTRATEGY.equals(emailResolver) ) {
            Mailer.UserProperty existing = user.getProperty(Mailer.UserProperty.class);
            if (existing==null || !existing.hasExplicitlyConfiguredAddress()) {
	        	String email = null;
	        	if ( OPTION_RESOLVER_ANYSTRATEGY.equals(emailResolver) ) {
	        		LOGGER.log(Level.FINE,"Calling any registered MailAddressResolver for {0}",new Object[]{user});
	        		email = MailAddressResolver.resolve(user);
	        	} else {
	                for (MailAddressResolver mar : MailAddressResolver.all()) {
	                	if ( mar.getClass().getName().equals(emailResolver) ) {
	                		LOGGER.log(Level.FINE,"Calling resolver {0} for {1}",new Object[]{emailResolver,user});
	                		email = mar.findMailAddressFor(user);
	                		break;
	                	}
                		LOGGER.log(Level.WARNING,"Resolver {0} not found : e-mail not updated",new Object[]{emailResolver});
	                }
	        	}
	        	if ( StringUtils.isNotBlank(email) ) {
	                try {
	              		LOGGER.log(Level.FINE,"Setting e-mail to {0}",new Object[]{email});
	                	user.addProperty(new Mailer.UserProperty(email));
	                } catch (IOException e){
	                	LOGGER.throwing(ScriptSecurityRealm.class.getCanonicalName(), "updateUserDetails", e);
	                }
	        	} else {
	        		LOGGER.log(Level.FINE,"Null or empty e-mail : not updating it");
	        	}
            } else {
            	LOGGER.log(Level.FINE,"An e-mail has already been set up by the user : not updating it");
            }
        } else {
        	LOGGER.log(Level.FINE,"None strategy : not updating the e-mail");
        }
    }

	public boolean isWindows() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.contains("win");
	}
}
