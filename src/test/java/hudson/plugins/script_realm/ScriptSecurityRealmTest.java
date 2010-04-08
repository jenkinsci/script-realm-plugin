package hudson.plugins.script_realm;

import org.acegisecurity.AuthenticationException;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ScriptSecurityRealmTest extends HudsonTestCase {
    public void test1() {
        new ScriptSecurityRealm("/bin/true").authenticate("test","test");
    }

    public void test2() {
        try {
            new ScriptSecurityRealm("/bin/false").authenticate("test","test");
            fail();
        } catch (AuthenticationException e) {
            // as expected
        }
    }
}
