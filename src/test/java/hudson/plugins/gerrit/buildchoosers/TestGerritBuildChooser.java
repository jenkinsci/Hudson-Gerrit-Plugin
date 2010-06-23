package hudson.plugins.gerrit.buildchoosers;

import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.spearce.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestGerritBuildChooser {

    GerritBuildChooser chooser;
    GitSCM gitSCM;
    IGitAPI git;
    GitUtils utils;
    BuildData data;
    ObjectId id;
    Build build;
    TaskListener listener;
    @Before
    public void init() {
        gitSCM = mock(GitSCM.class);
        git = mock(IGitAPI.class);
        utils = mock(GitUtils.class);
        listener = mock(TaskListener.class);
        data = mock(BuildData.class);
        String hexString = "1234567890123456789012345678901234567890";
        id = ObjectId.fromString(hexString);
        build = new Build(new Revision(id), 1, Result.SUCCESS);
        chooser = new GerritBuildChooser();


    }

    @Test
    public void noChangesPollCall() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(build);
        when(data.getLastBuiltRevision()).thenReturn(new Revision(id));
        DateTime now = new DateTime();

        when(git.getAllLogEntries("refs/changes/*")).thenReturn("\'" + id.name() + "#" + now.getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(true, "refs/changes/*", git, listener, data);
        assertEquals(0, results.size());
    }

    @Test
    public void noChangesBuild() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(build);
        when(data.getLastBuiltRevision()).thenReturn(new Revision(id));
        DateTime now = new DateTime();

        when(git.getAllLogEntries("refs/changes/*")).thenReturn("\'" + id.name() + "#" + now.getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(false, "refs/changes/*", git, listener, data);
        assertEquals(1, results.size());
        Iterator<Revision> iterator = results.iterator();

        assertEquals(id.name(), iterator.next().getSha1String());
    }

    @Test
    public void multipleCandidatesFirstBuild() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(null);
        when(data.getLastBuiltRevision()).thenReturn(null);
        DateTime now = new DateTime();
        String hexString = "2234567890123456789012345678901234567890";
        ObjectId id2 = ObjectId.fromString(hexString);

        when(git.getAllLogEntries("refs/changes/*")).thenReturn(
                "\'" + id.name() + "#" + now.getMillis()/1000 + "\'\n" +
                "\'" + id2.name() + "#" + now.plusMillis(5000).getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(true, "refs/changes/*", git, listener, data);
        assertEquals(2, results.size());
        Iterator<Revision> iterator = results.iterator();
        //No previous builds, so newest is first
        assertEquals(id2.name(), iterator.next().getSha1String());
        assertEquals(id.name(), iterator.next().getSha1String());

    }

    @Test
    public void firstBuild() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(null);
        when(data.getLastBuiltRevision()).thenReturn(null);
        DateTime now = new DateTime();
        
        when(git.getAllLogEntries("refs/changes/*")).thenReturn("\'" + id.name() + "#" + now.getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(true, "refs/changes/*", git, listener, data);
        assertEquals(1, results.size());
        assertEquals(id.name(), results.iterator().next().getSha1String());
    }

    @Test
    public void multipleCandidates() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(build);
        when(data.getLastBuiltRevision()).thenReturn(new Revision(id));
        DateTime now = new DateTime();
        ObjectId id2 = ObjectId.fromString("2234567890123456789012345678901234567890");
        ObjectId id3 = ObjectId.fromString("3234567890123456789012345678901234567890");
        when(git.getAllLogEntries("refs/changes/*")).thenReturn(
                "\'" + id.name() + "#" + now.getMillis()/1000 + "\'\n" +
                "\'" + id2.name() + "#" + now.plusMillis(10000).getMillis()/1000 + "\'\n" +
                "\'" + id3.name() + "#" + now.plusMillis(5000).getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(true, "refs/changes/*", git, listener, data);
        assertEquals(2, results.size());
        Iterator<Revision> iterator = results.iterator();
        //Had previous build, oldest should be first
        assertEquals(id3.name(), iterator.next().getSha1String());
        assertEquals(id2.name(), iterator.next().getSha1String());
    }

    @Test
    public void multipleCandidatesPreviousWasDifferentChooser() throws IOException {
        when(data.getLastBuildOfBranch("timebased")).thenReturn(build);
        when(data.getLastBuiltRevision()).thenReturn(null);
        DateTime now = new DateTime();
        ObjectId id2 = ObjectId.fromString("2234567890123456789012345678901234567890");
        ObjectId id3 = ObjectId.fromString("3234567890123456789012345678901234567890");

        when(git.getAllLogEntries("refs/changes/*")).thenReturn(
                "\'" + id.name() + "#" + now.getMillis()/1000 + "\'\n" +
                "\'" + id2.name() + "#" + now.plusMillis(10000).getMillis()/1000 + "\'\n" +
                "\'" + id3.name() + "#" + now.plusMillis(5000).getMillis()/1000 + "\'");
        Collection<Revision> results = chooser.getCandidateRevisions(true, "refs/changes/*", git, listener, data);
        assertEquals(3, results.size());
        Iterator<Revision> iterator = results.iterator();
        //Can't say what was last build for time based so this is considered as a new build. Newest is first
        assertEquals(id2.name(), iterator.next().getSha1String());
        assertEquals(id3.name(), iterator.next().getSha1String());
        assertEquals(id.name(), iterator.next().getSha1String());
    }

}
