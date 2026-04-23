package io.github.randomcodespace.iq.intelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link RepositoryIdentity}.
 * Validates graceful degradation when git metadata is unavailable.
 *
 * <p>Git invocations in these tests are hermetic: we override every global-config
 * setting that can cause {@code git commit} / {@code git init} to fail silently
 * on a developer machine but pass on CI (or vice-versa) — most notably
 * {@code commit.gpgsign}, {@code tag.gpgsign}, {@code core.hooksPath},
 * {@code init.templateDir}, and {@code core.autocrlf}. We also scrub
 * {@code GIT_CONFIG_*} / {@code GIT_DIR} / {@code GIT_WORK_TREE} env vars so the
 * invoked processes inherit no ambient git state from the parent.
 */
class RepositoryIdentityTest {

    // ------------------------------------------------------------------
    // Non-git directory — all git fields null, no exception
    // ------------------------------------------------------------------

    @Test
    void resolve_nonGitDirectory_allNullNoException(@TempDir Path dir) {
        assertThatCode(() -> RepositoryIdentity.resolve(dir)).doesNotThrowAnyException();

        RepositoryIdentity id = RepositoryIdentity.resolve(dir);
        assertThat(id.repoUrl()).isNull();
        assertThat(id.commitSha()).isNull();
        assertThat(id.branch()).isNull();
    }

    @Test
    void resolve_nonGitDirectory_buildTimestampAlwaysPresent(@TempDir Path dir) {
        RepositoryIdentity id = RepositoryIdentity.resolve(dir);
        assertThat(id.buildTimestamp()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Git repo with no remote — commitSha present, repoUrl null
    // ------------------------------------------------------------------

    @Test
    void resolve_gitRepoNoRemote_commitShaPopulated(@TempDir Path dir) throws Exception {
        initGitRepo(dir);

        RepositoryIdentity id = RepositoryIdentity.resolve(dir);
        // commit SHA may be null if no commits yet, but should not throw
        assertThat(id.repoUrl()).isNull();
        assertThat(id.buildTimestamp()).isNotNull();
    }

    @Test
    void resolve_gitRepoWithCommit_commitShaPresent(@TempDir Path dir) throws Exception {
        initGitRepo(dir);
        makeInitialCommit(dir);

        RepositoryIdentity id = RepositoryIdentity.resolve(dir);
        assertThat(id.commitSha()).isNotNull().isNotBlank();
        assertThat(id.repoUrl()).isNull();
    }

    // ------------------------------------------------------------------
    // Detached HEAD — branch normalised to null
    // ------------------------------------------------------------------

    @Test
    void resolve_detachedHead_branchIsNull(@TempDir Path dir) throws Exception {
        initGitRepo(dir);
        makeInitialCommit(dir);

        // Detach HEAD by checking out the commit SHA directly
        String sha = runGit(dir, "rev-parse", "HEAD");
        runGit(dir, "checkout", "--detach", sha);

        RepositoryIdentity id = RepositoryIdentity.resolve(dir);
        assertThat(id.branch()).isNull();
        assertThat(id.commitSha()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Record semantics
    // ------------------------------------------------------------------

    @Test
    void record_constructorAndAccessors() {
        var ts = java.time.Instant.now();
        var id = new RepositoryIdentity("https://github.com/x/y", "abc123", "main", ts);

        assertThat(id.repoUrl()).isEqualTo("https://github.com/x/y");
        assertThat(id.commitSha()).isEqualTo("abc123");
        assertThat(id.branch()).isEqualTo("main");
        assertThat(id.buildTimestamp()).isEqualTo(ts);
    }

    @Test
    void record_equalityOnSameValues() {
        var ts = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var id1 = new RepositoryIdentity("url", "sha", "main", ts);
        var id2 = new RepositoryIdentity("url", "sha", "main", ts);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void record_nullFieldsAllowed() {
        assertThatCode(() -> new RepositoryIdentity(null, null, null, java.time.Instant.now()))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Pre-flight: skip git-dependent tests when the {@code git} binary is not available.
     * Treat absence-of-git as an environment gap, not a product bug — still covered by the
     * non-git tests above, and by the {@code RepositoryIdentity.runGit} swallow-all-errors path.
     */
    private static void requireGit() {
        try {
            Process p = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true).start();
            boolean ok = p.waitFor() == 0;
            assumeTrue(ok, "git binary not available on PATH");
        } catch (Exception e) {
            assumeTrue(false, "git binary not available on PATH: " + e.getMessage());
        }
    }

    private static void initGitRepo(Path dir) throws Exception {
        requireGit();
        // -c overrides are applied to THIS invocation only and cannot be shadowed by a user's
        // global gitconfig (unlike `git config` writes into the new .git/config).
        run(dir, "git",
                "-c", "init.defaultBranch=main",
                "-c", "init.templateDir=",
                "init");
        run(dir, "git", "config", "user.email", "test@test.com");
        run(dir, "git", "config", "user.name", "Test");
        // Kill every global knob that can make `git commit` fail on an otherwise-clean repo.
        run(dir, "git", "config", "commit.gpgsign", "false");
        run(dir, "git", "config", "tag.gpgsign", "false");
        run(dir, "git", "config", "core.hooksPath", "/dev/null");
        run(dir, "git", "config", "core.autocrlf", "false");
    }

    private static void makeInitialCommit(Path dir) throws Exception {
        Path readme = dir.resolve("README.md");
        java.nio.file.Files.writeString(readme, "# Test");
        run(dir, "git", "add", ".");
        // --no-gpg-sign is belt-and-braces over the repo-local commit.gpgsign=false set above;
        // --allow-empty-message keeps the test robust if a commit.template hook injects content.
        run(dir, "git", "-c", "commit.gpgsign=false", "commit",
                "--no-gpg-sign", "-m", "init");
    }

    private static String runGit(Path dir, String... args) throws Exception {
        requireGit();
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git");
        cmd.addAll(java.util.Arrays.asList(args));
        var pb = new ProcessBuilder(cmd).directory(dir.toFile());
        scrubGitEnv(pb.environment());
        var proc = pb.start();
        String out;
        try (var is = proc.getInputStream()) {
            out = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        proc.waitFor();
        return out;
    }

    /**
     * Execute a git sub-command and assert it exited 0. Fails the test loudly (not silently)
     * if setup cannot complete — preferred over ignoring the exit code, which is what let the
     * GPG-signing failure slip through before.
     */
    private static void run(Path dir, String... cmd) throws Exception {
        var pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        scrubGitEnv(pb.environment());
        Process proc = pb.start();
        String stderr;
        try (var is = proc.getInputStream()) {
            stderr = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(
                    "Command failed (exit " + exit + "): " + String.join(" ", cmd)
                            + "\n" + stderr);
        }
    }

    /**
     * Remove every ambient git env var that could leak the parent shell's context into the
     * child process (most commonly {@code GIT_DIR}/{@code GIT_WORK_TREE} in a worktree-based
     * setup, or {@code GIT_CONFIG_*} injected by CI runners).
     */
    private static void scrubGitEnv(Map<String, String> env) {
        env.keySet().removeIf(k -> k.startsWith("GIT_"));
    }
}
