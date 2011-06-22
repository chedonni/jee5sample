package sample.core;

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sample.startup.Main;


class EclipseDropInsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseDropInsBuilder.class);

    public void build(Eclipse config) {

        // Define variables
        def workDir = new File(config.workDir)
        def eclipseDir = new File(workDir, "eclipse")
        def pluginsHomeDir =  new File(workDir, "dropins")
        def platformUrl = config.url
        def profile = config.profile
        def zipFileName = platformUrl.substring(platformUrl.lastIndexOf('/') + 1)
        def zipFileNameNoExt = zipFileName.substring(0, zipFileName.lastIndexOf('.'))
        def originalEclipseDir = new File(workDir, zipFileNameNoExt + "/eclipse")

        def ant = new AntBuilder()
        ant.mkdir (dir: workDir)
        if (!originalEclipseDir.exists()) {
            ant.get (src: platformUrl, dest: workDir, usetimestamp: true, verbose: true)
            ant.unzip (dest: new File(workDir, zipFileNameNoExt)) { fileset(dir: workDir){ include (name: platformUrl.substring(platformUrl.lastIndexOf('/') + 1))} }
        }

        // 1. Cache plugins which can be put into dropins/ folder later (i.e has plugin.dropinsName != null )
        for (Plugin plugin : config.plugins) {
            if (plugin.dropinsName != null) {
                def pluginTargetDir = new File(pluginsHomeDir, plugin.dropinsName)
                if (plugin.updateSites != null && !plugin.updateSites.empty) {
                    copyPluginFromUpdateSite(ant, profile, plugin.updateSites, plugin.featureIds, originalEclipseDir, eclipseDir, pluginTargetDir)
                } else {
                    copyPluginFromUrl(workDir, ant, profile, plugin.url, plugin.featureIds, originalEclipseDir, eclipseDir, pluginTargetDir)
                }
            }
        }

        // 2. Install plugins which must be put into core eclipse (i.e has plugin.dropinsName == null )
        ant.delete (dir: eclipseDir)
        ant.copy(todir: eclipseDir) {fileset(dir: originalEclipseDir)}
        for (Plugin plugin : config.plugins) {
            if (plugin.dropinsName == null) {
                def pluginTargetDir = eclipseDir
                if (plugin.updateSites != null && !plugin.updateSites.empty) {
                    copyPluginFromUpdateSite(ant, profile, plugin.updateSites, plugin.featureIds, originalEclipseDir, eclipseDir, pluginTargetDir)
                } else {
                    copyPluginFromUrl(workDir, ant, profile, plugin.url, plugin.featureIds, originalEclipseDir, eclipseDir, pluginTargetDir)
                }
            }
        }

        // 3. Copy dropins
        for (Plugin plugin : config.plugins) {
            if (plugin.dropinsName != null) {
                ant.copy(todir: new File(eclipseDir, "dropins/" + plugin.dropinsName)) {fileset(dir: new File(pluginsHomeDir, plugin.dropinsName))}
            }
        }

        // 4. Increase memory settings
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^\\-Xmx[0-9]+m", replace:"-Xmx800m", byline:"true");
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^[0-9]+m", replace:"400m", byline:"true");
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^[0-9]+M", replace:"400M", byline:"true");

        println "Congratulations! Your Eclipse IDE is ready. Location: " + eclipseDir.absolutePath

    }

    void copyPluginFromUrl(workDir, ant, profile, url, featureIds, originalEclipseDir, eclipseDir, pluginTargetDir) {
        if (!pluginTargetDir.exists() || pluginTargetDir.equals(eclipseDir)) {
            def fileName = url.substring(url.lastIndexOf('/') + 1)
            def downloadedFile = new File(workDir, fileName);
            if (!downloadedFile.exists()) {
                ant.get (src: url, dest: downloadedFile, usetimestamp: true, verbose: true)
            }
            List<String> names = new ArrayList<String>();
            ZipFile zf = new ZipFile(downloadedFile);
            for (Enumeration entries = zf.entries(); entries.hasMoreElements();) {
                String zipEntryName = ((ZipEntry)entries.nextElement()).getName();
                names.add(zipEntryName);
            }
            println "zip file content: " + names
            if (names.contains("plugin.xml") || names.contains("META-INF/")) {
                // is simple jar contains plugin
                ant.copy (file: downloadedFile, todir: new File(pluginTargetDir, "plugins"))
            } else if (names.contains("site.xml") || names.contains("content.jar") || names.contains("artifacts.jar")) {
                // is archive update site
                copyPluginFromUpdateSite(ant, profile, ["jar:" + downloadedFile.toURI().toURL().toString() + "!"], featureIds, originalEclipseDir, eclipseDir, pluginTargetDir)
            } else if (names.contains("eclipse/") || names.contains("plugins/")) {
                // is zipped plugins
                def tempDir = new File(workDir, downloadedFile.name + new Date().getTime())
                ant.unzip (src: downloadedFile, dest: tempDir)
                ant.copy(todir: pluginTargetDir){
                    fileset(dir: names.contains("eclipse/") ? new File(tempDir, "eclipse") : tempDir)
                }
                ant.delete(dir: tempDir)

            }
        }
    }

    void copyPluginFromUpdateSite(ant, profile, updateSites, featureIds, originalEclipseDir, eclipseDir, pluginTargetDir) {
        if (!pluginTargetDir.exists() || pluginTargetDir.equals(eclipseDir)) {
            def isWindows = (System.getProperty("os.name").indexOf("Windows") != -1);
            def javaPath = System.getProperty("java.home") + "/bin/java" + (isWindows ? ".exe" : "")
            def directorCmd = new CommandLine(javaPath)
            if (!pluginTargetDir.equals(eclipseDir)) {
                ant.delete (dir: eclipseDir)
                ant.copy(todir: eclipseDir) {fileset(dir: originalEclipseDir)}
            }
            def launcherPath = FileUtils.listFiles(new File(eclipseDir, "plugins"), new WildcardFileFilter("org.eclipse.equinox.launcher_*.jar"), FalseFileFilter.FALSE).get(0).absolutePath
            directorCmd.addArgument("-jar").addArgument(launcherPath)
            directorCmd.addArgument("-application").addArgument("org.eclipse.equinox.p2.director")
            directorCmd.addArgument("-profile").addArgument(profile)
            for (String updateSite : updateSites) {
                directorCmd.addArgument("-repository").addArgument(updateSite)
            }

            for (String featureId : featureIds) {
                ant.echo (message: "Will install " + featureId);
                directorCmd.addArgument("-installIU").addArgument(featureId)
            }
            directorCmd.addArgument("-consoleLog")
            def executor = new DefaultExecutor();
            executor.setExitValue(0);
            println directorCmd
            def exitValue = executor.execute(directorCmd);
            if (!pluginTargetDir.equals(eclipseDir)) {
                ant.copy(todir: new File(pluginTargetDir, "features")) {
                    fileset(dir: new File(eclipseDir, "features"), includes: "**/*") {
                        present (present: "srconly", targetdir: new File(originalEclipseDir, "features"))
                    }
                }
                ant.copy(todir: new File(pluginTargetDir, "plugins")) {
                    fileset(dir: new File(eclipseDir, "plugins"), includes: "**/*") {
                        present (present: "srconly", targetdir: new File(originalEclipseDir, "plugins"))
                    }
                }
            }
        }
    }
}