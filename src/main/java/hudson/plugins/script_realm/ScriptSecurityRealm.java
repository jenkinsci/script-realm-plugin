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
import hudson.Launcher.LocalLauncher;
import hudson.model.Descriptor;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.util.QuotedStringTokenizer;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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
 */
public class ScriptSecurityRealm extends AbstractPasswordBasedSecurityRealm {
	private static final Logger LOGGER = Logger.getLogger(ScriptSecurityRealm.class.getName());

	public final String commandLine;
	public final String groupsCommandLine;
	public final String groupsDelimiter;

	@DataBoundConstructor
	public ScriptSecurityRealm(String commandLine, String groupsCommandLine, String groupsDelimiter) {
		this.commandLine = commandLine;
		this.groupsCommandLine = groupsCommandLine;
		if (StringUtils.isBlank(groupsDelimiter)) {
			this.groupsDelimiter = ",";
		} else {
			this.groupsDelimiter = groupsDelimiter;
		}
	}

	protected UserDetails authenticate(String username, String password) throws AuthenticationException {
		try {
			StringWriter out = new StringWriter();
			LocalLauncher launcher = new LocalLauncher(new StreamTaskListener(out));
			if (launcher.launch().cmds(QuotedStringTokenizer.tokenize(commandLine)).stdout(new NullOutputStream()).envs("U=" + username, "P=" + password)
					.join() != 0) {
				throw new BadCredentialsException(out.toString());
			}
			GrantedAuthority[] groups = loadGroups(username);
			return new User(username, "", true, true, true, true, groups);
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
			return "Authenticate via custom script";
		}
	}

	protected GrantedAuthority[] loadGroups(String username) throws AuthenticationException {
		try {
			List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
			authorities.add(AUTHENTICATED_AUTHORITY);
			if (!StringUtils.isBlank(groupsCommandLine)) {
				StringWriter out = new StringWriter();
				LocalLauncher launcher = new LocalLauncher(new StreamTaskListener(out));
				OutputStream scriptOut = new ByteArrayOutputStream();
				if (launcher.launch().cmds(QuotedStringTokenizer.tokenize(groupsCommandLine)).stdout(scriptOut).envs("U=" + username).join() == 0) {
					StringTokenizer tokenizer = new StringTokenizer(scriptOut.toString().trim(), groupsDelimiter);
					while (tokenizer.hasMoreTokens()) {
						final String token = tokenizer.nextToken().trim();
						String[] args = new String[] { token, username };
						LOGGER.log(Level.FINE, "granting: {0} to {1}", args);
						authorities.add(new GrantedAuthorityImpl(token));
					}

				} else {
					throw new BadCredentialsException(out.toString());
				}
			}
			return authorities.toArray(new GrantedAuthority[0]);
		} catch (InterruptedException e) {
			throw new AuthenticationServiceException("Interrupted", e);
		} catch (IOException e) {
			throw new AuthenticationServiceException("Failed", e);
		}
	}
}
