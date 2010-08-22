package hudson.plugins.gerrit;

import hudson.FilePath;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.UnstableBuilder;
import org.mockito.Mock;


import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;


public class TestGerritNotifier extends GerritNotifierTestCase {

    BuildListener listener = null;
    @Override
    @BeforeClass
    public void setUp() throws Exception {
        super.setUp();
        listener = mock(BuildListener.class);
    }
    
    @Test
    public void testSettings() throws IOException, ExecutionException, InterruptedException {

        final Build build = doBuild(null);
        FilePath ws = build.getWorkspace();
        ws.act(new FilePath.FileCallable<Void>() {
            @Override
            public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                verify(git).getHead(f, ".git");
                return null;
            }
        });
        verify(marker).connect(hostName, 29418);
        verify(marker).authenticate(userName, new File(privateKeyFilePath), passPhrase);
    }



    @Test
    public void testUnstableBuild() throws IOException, ExecutionException, InterruptedException {

        final Build build = doBuild(new UnstableBuilder());
        assertEquals(Result.UNSTABLE, build.getResult());
        String command = notifier.generateUnstableCommand(notifier.getBuildUrl(build, listener), hexString);
        verify(marker).executeCommand(command);
    }

    @Test
    public void testStableBuild() throws IOException, ExecutionException, InterruptedException {

        final Build build = doBuild(null);
        assertEquals(Result.SUCCESS, build.getResult());
        
        String command = notifier.generateApproveCommand(notifier.getBuildUrl(build, listener), hexString);
        verify(marker).executeCommand(command);
    }

    @Test
    public void testFailedBuild() throws IOException, ExecutionException, InterruptedException {

        final Build build = doBuild(new FailureBuilder());
        assertEquals(Result.FAILURE, build.getResult());
        String command = notifier.generateFailedCommand(notifier.getBuildUrl(build, listener), hexString);
        verify(marker).executeCommand(command);
    }

    @Test
    public void testAbortedBuild() throws IOException, ExecutionException, InterruptedException {

        final Build build = doBuild(new MockBuilder(Result.ABORTED));
        assertEquals(Result.ABORTED, build.getResult());
        String command = notifier.generateDidNotFinishCommand(notifier.getBuildUrl(build, listener), hexString);
        verify(marker).executeCommand(command);
    }
}

