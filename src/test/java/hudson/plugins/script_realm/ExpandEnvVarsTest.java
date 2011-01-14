package hudson.plugins.script_realm;

import hudson.EnvVars;
import junit.framework.TestCase;

public class ExpandEnvVarsTest extends TestCase {

	/**
	 * assert the entered pwd does not get expanded, but stands as it is (does not get changed)
	 */
	public void testEnvVars() {
		String value = "dummy$$pwd";
		EnvVars m = LoginScriptLauncher.inherit(new String[]{"U=user","P="+value});
		String expandedValue = m.get("P");
		assertEquals(value, expandedValue);
	}
}
