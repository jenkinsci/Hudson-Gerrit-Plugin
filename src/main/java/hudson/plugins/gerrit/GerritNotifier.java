package hudson.plugins.gerrit;

import hudson.plugins.gerrit.git.GitTools;
import hudson.plugins.gerrit.ssh.SSHMarker;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 *
 */
@SuppressWarnings({"UnusedDeclaration"})
public class GerritNotifier extends Notifier implements Serializable {

    private final String git_home;
    private final String gerrit_host;
    private final int gerrit_port;
    private final String gerrit_username;

    private final String approve_value;
    private final String unstable_value;
    private final String reject_value;
    private final String gerrit_approve_command = "gerrit approve --verified=%s --message=\"%s\" %s";
    protected static final String NO_BUILD_URL = "No build url.";
    private final String private_key_file_path;
    private final String passPhrase;

    transient SSHMarker marker;
    transient GitTools git;


    public void setMarker(SSHMarker marker) {
        this.marker = marker;
    }

    public void setGitTools(GitTools git) {
        this.git = git;
    }

    public String getGerrit_host() {
        return gerrit_host;
    }

    public int getGerrit_port() {
        return gerrit_port;
    }

    public String getGerrit_username() {
        return gerrit_username;
    }

    public String getApprove_value() {
        return approve_value;
    }

    public String getUnstable_value() {
        return unstable_value;
    }
    public String getReject_value() {
        return reject_value;
    }

    public String getGerrit_approve_command() {
        return gerrit_approve_command;
    }

    public String getPrivate_key_file_path() {
        return private_key_file_path;
    }

    public String getPassPhrase() {
        return passPhrase;
    }


    @SuppressWarnings({"UnusedDeclaration"})
    @DataBoundConstructor
    public GerritNotifier(String git_home, String gerrit_host, int gerrit_port,
            String gerrit_username, String approve_value, String unstable_value, String reject_value, String private_key_file_path,
            String passPhrase) {
        this.git_home = git_home;
        this.gerrit_host = gerrit_host;
        this.gerrit_port = gerrit_port;
        this.gerrit_username = gerrit_username;
        this.approve_value = approve_value;
        this.unstable_value = unstable_value;
        this.reject_value = reject_value;
        this.private_key_file_path = private_key_file_path;
        this.passPhrase = passPhrase;
        this.marker = new SSHMarker();
        this.git = new GitTools();

    }

    public String getGit_home() {
        return git_home;
    }


    public String generateComment(String verify_value, String message, String revision) {
        return String.format(gerrit_approve_command, verify_value, message, revision);
    }

    public String generateApproveCommand(final String jobUrl, final String revision) {
        return generateComment(approve_value, jobUrl, revision);
    }

    public String generateUnstableCommand(final String jobUrl, final String revision) {
        return generateComment(unstable_value, "Build is unstable " + jobUrl, revision);
    }

    public String generateFailedCommand(final String jobUrl, final String revision) {
        return generateComment(reject_value, "Build failed " + jobUrl, revision);
    }

    public String generateDidNotFinishCommand(final String jobUrl, final String revision) {
        return generateComment("0", "Build did not finish, " + jobUrl, revision);
    }

    private void verifyGerrit(String message)
            throws IOException, InterruptedException {

        File privateKeyFile = new File(private_key_file_path);
        marker.connect(gerrit_host, gerrit_port);
        marker.authenticate(gerrit_username, privateKeyFile, passPhrase);
        marker.executeCommand(message);
        marker.disconnect();
    }


    @Override
    public boolean perform(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {

        FilePath ws = build.getWorkspace();

        //Hacky
        if (marker == null) {
            marker = new SSHMarker();
        }
        String head = ws.act(new FileCallable<String>() {
            // if 'file' is on a different node, this FileCallable will
            // be transferred to that node and executed there.

            public String invoke(File workspace, VirtualChannel channel) throws IOException{
                // f and file represents the same thing
                if (git == null) {
                    git = new GitTools();
                }
                return git.getHead(workspace, GerritNotifier.this.git_home).name();
            }
        });
        try {
            Result r = build.getResult();

            String buildUrl = getBuildUrl(build, listener);
            if (r == Result.ABORTED || r == Result.NOT_BUILT) {
                listener.getLogger().println("Build was aborted, notifying gerrit");
                verifyGerrit(generateDidNotFinishCommand(buildUrl, head));
            } else {

                if (r.isBetterOrEqualTo(Result.SUCCESS)) {
                    listener.getLogger().println("Approving " + head);
                    verifyGerrit(generateApproveCommand(buildUrl, head));
                } else if (r.isBetterOrEqualTo(Result.UNSTABLE)) {
                    listener.getLogger().println("Rejecting unstable " + head);
                    verifyGerrit(generateUnstableCommand(buildUrl, head));
                } else {
                    listener.getLogger().println("Rejecting failed " + head);
                    verifyGerrit(generateFailedCommand(buildUrl, head));
                }
            }
        } catch (IOException e) {
            listener.getLogger().println(e.getMessage());
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.ABORTED);
            return false;
        } catch (InterruptedException e) {
            listener.getLogger().println("Interrupted: " + e.getMessage());
            build.setResult(Result.ABORTED);
        }

        return true;
    }

    String getBuildUrl(AbstractBuild build, BuildListener listener) throws IOException {
        EnvVars vars = null;
        try {
            vars = build.getEnvironment(listener);
        } catch (InterruptedException e) {
            listener.getLogger().println(e.getMessage());
            e.printStackTrace();
            return NO_BUILD_URL;
        }
        String buildUrl = NO_BUILD_URL;
        if (vars.containsKey("BUILD_URL")) {
            buildUrl = vars.get("BUILD_URL");
        }
        return buildUrl;
    }

    // overrided for better type safety.
    // if your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor for {@link GerritNotifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/GerritNotifier/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {


        String path_to_private_key_file;

        public FormValidation doCheckGerrit_username(@QueryParameter String value)  {
            if (value.length() == 0) {
                return FormValidation.error("Please set a name");
            }
            return FormValidation.ok();

        }

        public FormValidation doCheckGerrit_host(@QueryParameter String value)  {
            if (value.length() == 0) {
                return FormValidation.error("Please set a gerritHost");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPrivate_key_file_path(@QueryParameter String value)  {
            if (value.length() == 0) {
                return FormValidation.error("Please set a path to private key file");
            }
            File f = new File(value);
            if(!f.exists())
            {
                return FormValidation.error("File doesn't exists");
            }
            if (!SSHMarker.IsPrivateKeyFileValid(f))
            {
                return FormValidation.error("Private key file is not valid");
            }
            path_to_private_key_file = value;

            return FormValidation.ok();
        }

        public FormValidation doCheckPassPhrase(@QueryParameter String value) {

            if(path_to_private_key_file == null) {
                return FormValidation.error("Define path to private key file first");
            }
            File f = new File(path_to_private_key_file);
            if(!f.exists())
            {
                return FormValidation.error("No private key file");
            }
            if (!SSHMarker.IsPrivateKeyFileValid(f))
            {
                return FormValidation.error("Private key file is not valid");
            }
            if (!SSHMarker.CheckPassPhrase(f, value))
            {
                return FormValidation.error("Passphrase is not valid");
            }
            return FormValidation.ok();
        }



        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Gerrit Integration";
        }

        public String guessSSHKeyFile() {
            String user_home = System.getProperty("user.home");
            String ssh_path = user_home + File.separatorChar + ".ssh" + File.separatorChar;

            File f = new File(ssh_path + "id_dsa");
            if(f.exists()) {
                return ssh_path + "id_dsa";
            }
            f = new File(ssh_path + "id_rsa");
            if(f.exists()) {
                return ssh_path + "id_rsa";
            }
            return "";
        }
    }
}

