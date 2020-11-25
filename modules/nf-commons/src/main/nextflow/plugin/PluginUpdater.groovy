/*
 * Copyright 2020, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package nextflow.plugin

import static java.nio.file.StandardCopyOption.*

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.extension.FilesEx
import nextflow.file.FileHelper
import org.pf4j.PluginManager
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginState
import org.pf4j.update.DefaultUpdateRepository
import org.pf4j.update.PluginInfo
import org.pf4j.update.UpdateManager
import org.pf4j.update.UpdateRepository
import org.pf4j.util.FileUtils

import nextflow.file.FileMutex
/**
 * Implements the download/install/update for nextflow plugins
 *
 * Plugins are downloaded and stored uncompressed in the
 * directory defined by the variable `NXF_PLUGINS_DIR`, $NXF_HOME/nextflow
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class PluginUpdater extends UpdateManager {

    private PluginManager pluginManager

    private Path pluginsStore

    PluginUpdater(PluginManager pluginManager, Path pluginsRoot, URL repo) {
        super(pluginManager, wrap(repo))
        this.pluginsStore = pluginsRoot
        this.pluginManager = pluginManager
    }

    static private List<UpdateRepository> wrap(URL repo) {
        List<UpdateRepository> result = new ArrayList<>(1)
        result << new DefaultUpdateRepository('nextflow.io', repo)
        return result
    }

    /**
     * Install a new plugin downloading the artifact from the remote source if needed
     *
     * @param id The plugin Id
     * @param version The plugin version
     * @return {@code true} when the plugin is correctly download, installed and started, {@code false} otherwise
     */
    @Override
    boolean installPlugin(String id, String version) {
        if( !pluginsStore )
            throw new IllegalStateException("Missing pluginStore attribute")

        return load0(id, version)
    }

    private Path download0(String id, String version) {

        // 0. check if already exists
        final pluginPath = pluginsStore.resolve("$id-$version")
        if( FilesEx.exists(pluginPath) ) {
            return pluginPath
        }

        // 1. Download to temporary location
        Path downloaded = downloadPlugin(id, version);

        // 2. unzip the content and delete download filed
        Path dir = FileUtils.expandIfZip(downloaded)
        FileHelper.deletePath(downloaded)

        // 3. move the final destination the plugin directory
        assert pluginPath.getFileName() == dir.getFileName()

        try {
            Files.move(dir, pluginPath, REPLACE_EXISTING, ATOMIC_MOVE);
        }
        catch (IOException e) {
            throw new PluginRuntimeException(e, "Failed to write file '$pluginPath' to plugins folder");
        }

        return pluginPath
    }

    /**
     * Race condition safe plugin download. Multiple instaces are synchronised
     * using a file system lock created in the tmp directory
     *
     * @param id The plugin Id
     * @param version The plugin version string
     * @return The uncompressed plugin directory path
     */
    private Path safeDownload(String id, String version) {
        final sentinel = lockFile(id,version)
        final mutex = new FileMutex(target: sentinel, timeout: '10 min')
        try {
            return mutex.lock { download0(id, version) }
        }
        finally {
            sentinel.deleteDir()
        }
    }

    /**
     * Get the file to be used a lock to synchronise concurrent downloaded from multiple Nextflow launches
     *
     * @param id The plugin Id
     * @param version The plugin version
     * @return The lock the path
     */
    private File lockFile(String id, String version) {
        def tmp = System.getProperty('java.io.tmpdir')
        new File(tmp, "nextflow-plugin-${id}-${version}.lock")
    }

    private boolean load0(String id, String version) {

        def pluginPath = pluginsStore.resolve("$id-$version")
        if( !FilesEx.exists(pluginPath) ) {
            pluginPath = safeDownload(id,version)
        }

        String pluginId = pluginManager.loadPlugin(pluginPath);
        PluginState state = pluginManager.startPlugin(pluginId);

        return PluginState.STARTED == state
    }

    /**
     * Update an existing plugin, unloading the current version and installing
     * the requested one downloading the artifact if needed
     *
     * @param id The plugin Id
     * @param version The plugin version
     * @return {@code true} when the plugin is updated and started or {@code false} otherwise
     */
    @Override
    boolean updatePlugin(String id, String version) {
        if (pluginManager.getPlugin(id) == null) {
            throw new PluginRuntimeException("Plugin $id cannot be updated since it is not installed")
        }

        PluginInfo pluginInfo = getPluginsMap().get(id);
        if (pluginInfo == null) {
            throw new PluginRuntimeException("Plugin $id does not exist in any repository")
        }

        if (!pluginManager.deletePlugin(id)) {
            return false;
        }

        load0(id, version)
    }
}
