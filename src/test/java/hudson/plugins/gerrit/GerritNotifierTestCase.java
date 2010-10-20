package hudson.plugins.gerrit;

import hudson.plugins.gerrit.git.GitTools;
import hudson.plugins.gerrit.ssh.SSHMarker;
import hudson.model.Build;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.mockito.Matchers;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class GerritNotifierTestCase extends HudsonTestCase{

    SSHMarker marker;
    GitTools git;

    String userName = "user";
    String hostName = "localhost";
    String gitDir = ".git";
    final String privateKeyFilePath = "private_key_path";
    String passPhrase = "";
    String hexString = "1234567890123456789012345678901234567890";
    ObjectId id = ObjectId.fromString(hexString);
    GerritNotifier notifier;

    @Override
    @BeforeClass
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        marker = mock(SSHMarker.class);
        git = mock(GitTools.class);
        notifier = new GerritNotifier(gitDir, hostName, 29418, userName, "+1", "-1", "-1",
            privateKeyFilePath, passPhrase);
        notifier.setGitTools(git);
        notifier.setMarker(marker);
        when(git.getHead(Matchers.<File>any(), eq(".git"))).thenReturn(id);
    }
    
    protected Build doBuild(Builder builder) throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject project = createProject(builder);
        return project.scheduleBuild2(0).get();
    }

    protected FreeStyleProject createProject(Builder builder) throws IOException {
        FreeStyleProject project = createFreeStyleProject();
        if(builder != null) {
            project.getBuildersList().add(builder);
        }

        project.getPublishersList().add(notifier);
        return project;
    }

    protected FreeStyleProject createProject() throws IOException {
        return createProject(null);
    }

    @Test
    public void testInitialization() throws IOException {
        //Just a random test because otherwise JUnit complains that it can't find any tests here.
        FreeStyleProject project = createProject(null);
        assertEquals(1, project.getPublishersList().size());
        
    } 

}
