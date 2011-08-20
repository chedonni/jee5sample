package sample.core;

import java.security.MessageDigest;
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.Comparator

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.NameFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;


class EclipseDropInsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseDropInsBuilder.class);

    public void build(Eclipse config) throws Exception {
        def ant = new AntBuilder()

        def workDir = new File(config.workDir)
        ant.mkdir (dir: workDir)

        def platformUrl = config.url
        def profile = config.profile

        def zipFileName = platformUrl.substring(platformUrl.lastIndexOf('/') + 1)
        def zipFileNameNoExt = zipFileName.substring(0, zipFileName.lastIndexOf('.'))
        def platformEclipseDir = new File(workDir, zipFileNameNoExt + "/eclipse")
        if (!platformEclipseDir.exists()) {
             ant.get (src: platformUrl, dest: workDir, usetimestamp: true, verbose: true)
             ant.unzip (dest: new File(workDir, zipFileNameNoExt)) { fileset(dir: workDir){ include (name: platformUrl.substring(platformUrl.lastIndexOf('/') + 1))} }
        }

        def eclipseDir = new File(workDir, "eclipse")
        ant.delete (dir: eclipseDir)
        ant.copy(todir: eclipseDir) {fileset(dir: platformEclipseDir)}
        def snapshotDir = new File(workDir, "snapshot")
        ant.delete (dir: snapshotDir)
        Map<Plugin, File> cachedPlugins = new Hashtable<Plugin, File>();
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.update(platformUrl.getBytes("UTF-8"))
        for (Plugin plugin : config.plugins) {
            // try the cache first
            List<String> values = new ArrayList<String>();
            values.add(plugin.url)
            for (String s : plugin.updateSites) values.add(s)
            for (String s : plugin.featureIds) values.add(s)
            for (String s : values) {
                if (s != null) md.update(s.getBytes("UTF-8"))
            }
            String id = Hex.encodeHexString(md.clone().digest())
            id = (plugin.dropinsName != null ? plugin.dropinsName : "") + "_" + id.substring(0, 5)
            File cachedPlugin = new File(new File(workDir,"cached"), id);
            println "Install plugin ${plugin.dropinsName} into ${id}"
            cachedPlugins.put(plugin, cachedPlugin)
            if (cachedPlugin.exists()) {
                // 1. create a snapshot
                ant.copy(todir: snapshotDir) {fileset(dir: eclipseDir)}
                // find cached files for plugin, use them
                ant.copy(todir: eclipseDir) {fileset(dir: cachedPlugin)}
            } else {
                // 1. create a snapshot
                ant.copy(todir: snapshotDir) {fileset(dir: eclipseDir)}
                // 2. install
                if (plugin.url != null) {
                    installFromUrl(eclipseDir, workDir, ant, profile, plugin.url, plugin.featureIds)
                } else {
                    installFromUpdateSite(eclipseDir, ant, profile, plugin.updateSites, plugin.featureIds)
                }
                // 3. compare with the snapshot and save new files to cachedPlugin folder
                ant.copy(todir: new File(cachedPlugin, "features")) {
                        fileset(dir: new File(eclipseDir, "features"), includes: "**/*") {
                            present (present: "srconly", targetdir: new File(snapshotDir, "features"))
                        }
                }
                ant.copy(todir: new File(cachedPlugin, "plugins")) {
                    fileset(dir: new File(eclipseDir, "plugins"), includes: "**/*") {
                        present (present: "srconly", targetdir: new File(snapshotDir, "plugins"))
                    }
                }
				// 4. test new jar/zip files broken or not
				try {
					ant.delete (dir: new File(workDir, "unzipped"))
					ant.unzip(dest: new File(workDir, "unzipped")) {
						fileset(dir: cachedPlugin, includes: "**/*.jar **/*.zip")
					}
				} catch (Exception e) {
					ant.delete (dir: cachedPlugin)
					throw e;
				}
            }
        }

        ant.delete (dir: eclipseDir)
        ant.copy(todir: eclipseDir) {fileset(dir: platformEclipseDir)}
        for (Plugin plugin : config.plugins) {
            File cachedPlugin = cachedPlugins.get(plugin);
            if (plugin.dropinsName != null) {
                ant.copy(todir: new File(eclipseDir, "dropins/" + plugin.dropinsName)) {fileset(dir: cachedPlugin)}
            } else {
                ant.copy(todir: eclipseDir) {fileset(dir: cachedPlugin)}
            }
        }

        // 4. Increase memory settings
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^\\-Xmx[0-9]+m", replace:"-Xmx800m", byline:"true");
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^[0-9]+m", replace:"400m", byline:"true");
        ant.replaceregexp (file: new File(eclipseDir, "eclipse.ini"),  match:"^[0-9]+M", replace:"400M", byline:"true");

		// 5. Remove conflicting key binding from Aptana plugin (if any)
		def files = FileUtils.listFiles(new File(eclipseDir, "dropins"), new WildcardFileFilter("com.aptana.editor.common_*.jar"), TrueFileFilter.INSTANCE);
		if (!files.isEmpty()) {
			ant.delete(file: new File(workDir, "plugin.xml"))
			ant.unzip (src: files.get(0), dest: workDir){
				patternset {include (name:"plugin.xml")}
			}
			ant.replaceregexp (file: new File(workDir, "plugin.xml"),  match:"<key[^<]+CTRL\\+SHIFT\\+R[^<]+</key>", replace:"", flags:"s");
			ant.jar(destfile:files.get(0), basedir:workDir,includes:"plugin.xml",update:true)
		}
		files = FileUtils.listFiles(new File(eclipseDir, "dropins"), new WildcardFileFilter("com.aptana.syncing.ui_*.jar"), TrueFileFilter.INSTANCE);
		if (!files.isEmpty()) {
			ant.delete(file: new File(workDir, "plugin.xml"))
			ant.unzip (src: files.get(0), dest: workDir){
				patternset {include (name:"plugin.xml")}
			}
			ant.replaceregexp (file: new File(workDir, "plugin.xml"),  match:"<key[^<]+M1\\+M2\\+U[^<]+</key>", replace:"", flags:"s");
			ant.jar(destfile:files.get(0), basedir:workDir,includes:"plugin.xml",update:true)
		}

		// 6. Launch profiles support
		configureProfiles(ant, eclipseDir, config)

        println "Congratulations! Your Eclipse IDE is ready. Location: " + eclipseDir.absolutePath
        println "Remember to remove spring-uaa, spring-roo plugins/features and change Aptana theme to eclipse theme"
		println "You may want to use Envy Code R font (http://damieng.com/blog/2008/05/26/envy-code-r-preview-7-coding-font-released)"
		println "Or consolas font (on WinXP): 1. http://www.hanselman.com/blog/ConsolasFontFamilyNowAvailableForDownload.aspx  2.turn on ClearType(http://blogs.microsoft.co.il/blogs/kim/archive/2006/05/03/289.aspx)"
    }

	void configureProfiles(ant, eclipseDir, Eclipse config) {
		def profilesDir = new File(eclipseDir, "profiles")
		ant.move (todir: new File(profilesDir, "dropins")){fileset(dir: new File(eclipseDir, "dropins"))}
		for (Profile profile : config.profiles) {
			File linksDir = new File(profilesDir, "profile_" + profile.getProfileName())
			ant.mkdir(dir: linksDir)
			for (String name : profile.getDropinsNames()) {
				String path = "path=profiles/dropins/" + name;
				FileUtils.write(new File(linksDir, name + ".link"), path)
			}
			String batch = 'rmdir /S /Q "%~dp0..\\links"' +
			"\r\n" +  'mkdir "%~dp0..\\links"' +
			"\r\n" +  'xcopy /E "%~dp0profile_' + profile.getProfileName() + '" "%~dp0..\\links"' +
			"\r\n" +  'start "" "%~dp0..\\eclipse.exe"' + "\r\n";
			FileUtils.write(new File(profilesDir, "profile_" + profile.getProfileName() + ".bat"), batch)
		}
		File linksDir = new File(profilesDir, "profile_all")
		ant.mkdir(dir: linksDir)
		for (Plugin plugin : config.plugins) {
			if (plugin.dropinsName != null) {
				String path = "path=profiles/dropins/" + plugin.dropinsName;
				FileUtils.write(new File(linksDir, plugin.dropinsName + ".link"), path)
			}
		}
		String batch = 'rmdir /S /Q "%~dp0..\\links"' +
			"\r\n" +  'mkdir "%~dp0..\\links"' +
			"\r\n" +  'xcopy /E "%~dp0profile_all" "%~dp0..\\links"' +
			"\r\n" +  'start "" "%~dp0..\\eclipse.exe"' + "\r\n";
		FileUtils.write(new File(profilesDir, "profile_all.bat"), batch)
	}

	void configureProfiless(ant, eclipseDir, Eclipse config) {
		if (config.profiles != null && !config.profiles.isEmpty()) {
			ant.rename (src: new File(eclipseDir, "dropins"), dest: new File(eclipseDir, "links_content"))
			ant.mkdir(dir: new File(eclipseDir, "links"))
			for (Profile profile : config.profiles) {
				FileWriter w = new FileWriter(new File(new File(eclipseDir, "links"), profile.getProfileName() + ".link"));
				for (String name : profile.getDropinsNames()) {
					w.write("path=links_content/" + name + "\n")
				}
				w.close();
			}
		}
	}
    void installFromUpdateSite(eclipseDir, ant, profile, updateSites, featureIds) {
        def isWindows = (System.getProperty("os.name").indexOf("Windows") != -1);
        def javaPath = System.getProperty("java.home") + "/bin/java" + (isWindows ? ".exe" : "")
        def directorCmd = new CommandLine(javaPath)

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

    }

    void installFromUrl(eclipseDir, workDir, ant, profile, url, featureIds) {
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
            ant.copy (file: downloadedFile, todir: new File(eclipseDir, "plugins"))
        } else if (names.contains("site.xml") || names.contains("content.jar") || names.contains("artifacts.jar")) {
            // is archive update site
            installFromUpdateSite(eclipseDir, ant, profile, ["jar:" + downloadedFile.toURI().toURL().toString() + "!/"], featureIds)
        } else {
            // is zipped plugins
            def tempDir = new File(workDir, downloadedFile.name + new Date().getTime())
            ant.unzip (src: downloadedFile, dest: tempDir)
            try {
                if (names.contains("eclipse/") || names.contains("plugins/")) {
                    ant.copy(todir: eclipseDir){
                        fileset(dir: names.contains("eclipse/") ? new File(tempDir, "eclipse") : tempDir)
                    }
                } else {
                    Collection files = FileUtils.listFiles(tempDir, new NameFileFilter("plugin.xml"), TrueFileFilter.INSTANCE);
                    if (!files.isEmpty()) {
                        def fileList = []
                        fileList.addAll(files);
                        Collections.sort(fileList, new Comparator<File>(){
                                public int compare(File f1, File f2) {
                                        return f1.getAbsolutePath().length() - f2.getAbsolutePath().length();
                                }

                        });
                        File file = fileList.iterator().next();
                        if (file.getParentFile().getParentFile().getName().equals("plugins")) {
                                ant.copy(todir: eclipseDir){
                                        fileset(dir: file.getParentFile().getParentFile().getParentFile())
                                }
                        } else {
                            ant.copy(todir: new File(eclipseDir, "plugins")){
                                fileset(dir: file.getParentFile().getParentFile())
                            }
                        }
                    }
                }
            } finally {
                ant.delete(dir: tempDir)
            }
        }
    }
}
