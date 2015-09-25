/**
 * 
 */
package hudson.plugins.script_realm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.Util;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This launcher does not expand the given environment variables - this is
 * needed, as the password and user should be past to the script as they are
 * entered in the UI. (e.g. '$$' should not be expanded to '$', but stay as it
 * is)
 * 
 * @author domi
 * 
 */
public class LoginScriptLauncher extends LocalLauncher {

	private static final String CLASSNAME = LoginScriptLauncher.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLASSNAME);

	public LoginScriptLauncher(TaskListener listener) {
		super(listener);
	}

	@Override
	public Proc launch(ProcStarter ps) throws IOException {
		maskedPrintCommandLine(ps.cmds(), ps.masks(), ps.pwd());

		EnvVars jobEnv = inherit(ps.envs());

		// replace variables in command line
		String[] jobCmd = new String[ps.cmds().size()];
		for (int idx = 0; idx < jobCmd.length; idx++) {
			jobCmd[idx] = jobEnv.expand(ps.cmds().get(idx));
		}

		try {
			return new hudson.Proc.LocalProc(jobCmd, Util.mapToEnv(jobEnv), ps.stdin(), ps.stdout(), ps.stderr(), toFile(ps.pwd()));
		} catch ( IOException e ) {
			// logs useful details about the command being run like variables as they were replaced
			LOGGER.log(Level.SEVERE, String.format("Error while executing command %s",Arrays.toString(jobCmd)), e);
			throw e;
		}
	}

	private File toFile(FilePath f) {
		return f == null ? null : new File(f.getRemote());
	}

	/**
	 * Expands the list of environment variables.
	 */
	public static EnvVars inherit(String[] env) {
		// convert String[] to Map
		EnvVars m = new EnvVars();
		if (env != null) {
			for (String e : env) {
				int index = e.indexOf('=');
				m.put(e.substring(0, index), e.substring(index + 1));
			}
		}
		// at this point the original implementation
		// (hudson.Launcher.LocalLauncher) was expanding the variables.
		return m;
	}

}
