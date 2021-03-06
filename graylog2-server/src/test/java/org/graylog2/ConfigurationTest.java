/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2;

import com.github.joschi.jadconfig.JadConfig;
import com.github.joschi.jadconfig.ParameterException;
import com.github.joschi.jadconfig.RepositoryException;
import com.github.joschi.jadconfig.ValidationException;
import com.github.joschi.jadconfig.repositories.InMemoryRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Files.newTemporaryFile;

public class ConfigurationTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private Map<String, String> validProperties;

    @Before
    public void setUp() throws Exception {
        validProperties = new HashMap<>();

        // Required properties
        validProperties.put("password_secret", "ipNUnWxmBLCxTEzXcyamrdy0Q3G7HxdKsAvyg30R9SCof0JydiZFiA3dLSkRsbLF");
        // SHA-256 of "admin"
        validProperties.put("root_password_sha2", "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918");
    }

    @Test
    public void testPasswordSecretIsTooShort() throws ValidationException, RepositoryException {
        validProperties.put("password_secret", "too short");

        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("The minimum length for \"password_secret\" is 16 characters.");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();
    }

    @Test
    public void testPasswordSecretIsEmpty() throws ValidationException, RepositoryException {
        validProperties.put("password_secret", "");

        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Parameter password_secret should not be blank");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();
    }

    @Test
    public void testPasswordSecretIsNull() throws ValidationException, RepositoryException {
        validProperties.put("password_secret", null);

        expectedException.expect(ParameterException.class);
        expectedException.expectMessage("Required parameter \"password_secret\" not found.");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();
    }

    @Test
    public void testPasswordSecretIsValid() throws ValidationException, RepositoryException {
        validProperties.put("password_secret", "abcdefghijklmnopqrstuvwxyz");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        assertThat(configuration.getPasswordSecret()).isEqualTo("abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    public void testNodeIdFilePermissions() throws IOException, ValidationException {
        final Path nonEmptyNodeIdPath = newTemporaryFile().toPath();
        final Path emptyNodeIdPath = newTemporaryFile().toPath();
        try {
            // create a node-id file and write some id
            Files.write(nonEmptyNodeIdPath, "test-node-id".getBytes(StandardCharsets.UTF_8), WRITE, TRUNCATE_EXISTING);
            assertThat(nonEmptyNodeIdPath.toFile().length()).isGreaterThan(0);

            // make sure this file isn't accidentally present
            final Path missingNodeIdPath = newTemporaryFile().toPath();
            assertThat(missingNodeIdPath.toFile().delete()).isTrue();

            // read/write permissions should make the validation pass
            assertThat(validateWithPermissions(nonEmptyNodeIdPath, "rw-------")).isTrue();
            assertThat(validateWithPermissions(emptyNodeIdPath, "rw-------")).isTrue();

            // existing, but not writable is ok if the file is not empty
            assertThat(validateWithPermissions(nonEmptyNodeIdPath, "r--------")).isTrue();

            // existing, but not writable is not ok if the file is empty
            assertThat(validateWithPermissions(emptyNodeIdPath, "r--------")).isFalse();

            // existing, but not readable is not ok
            assertThat(validateWithPermissions(nonEmptyNodeIdPath, "-w-------")).isFalse();

            // missing, but not writable is not ok
            assertThat(validateWithPermissions(missingNodeIdPath, "r--------")).isFalse();
        } finally {
            Files.delete(nonEmptyNodeIdPath);
            Files.delete(emptyNodeIdPath);
        }
    }

    /**
     * Run the NodeIDFileValidator on a file with the given permissions.
     * @param nodeFilePath the path to the node id file, can be missing
     * @param permissions the posix permission to set the file to, if it exists, before running the validator
     * @return true if the validation was successful, false otherwise
     * @throws IOException if any file related problem occurred
     */
    private static boolean validateWithPermissions(Path nodeFilePath, String permissions) throws IOException {
        try {
            final Configuration.NodeIdFileValidator validator = new Configuration.NodeIdFileValidator();
            if (nodeFilePath.toFile().exists()) {
                Files.setPosixFilePermissions(nodeFilePath, PosixFilePermissions.fromString(permissions));
            }
            validator.validate("node-id", nodeFilePath.toString());
        } catch (ValidationException ve) {
            return false;
        }
        return true;
    }
}
