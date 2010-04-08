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
import hudson.model.TaskListener;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.util.QuotedStringTokenizer;
import hudson.util.StreamTaskListener;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationServiceException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.io.output.NullOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.io.StringWriter;

/**
 * @author Kohsuke Kawaguchi
 */
public class ScriptSecurityRealm extends AbstractPasswordBasedSecurityRealm {
    public final String commandLine;

    @DataBoundConstructor
    public ScriptSecurityRealm(String commandLine) {
        this.commandLine = commandLine;
    }

    protected UserDetails authenticate(String username, String password) throws AuthenticationException {
        try {
            StringWriter out = new StringWriter();
            LocalLauncher launcher = new LocalLauncher(new StreamTaskListener(out));
            if (launcher.launch().cmds(QuotedStringTokenizer.tokenize(commandLine)).stdout(new NullOutputStream())
                    .envs("U="+username,"P="+password).join()!=0)
                throw new BadCredentialsException(out.toString());

            return new User(username,"",true,true,true,true,new GrantedAuthority[]{AUTHENTICATED_AUTHORITY});
        } catch (InterruptedException e) {
            throw new AuthenticationServiceException("Interrupted",e);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Failed",e);
        }
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        return new User(username,"",true,true,true,true,new GrantedAuthority[]{AUTHENTICATED_AUTHORITY});
    }

    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        throw new UsernameNotFoundException(groupname);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return "Authenticate via custom script";
        }
    }
}
