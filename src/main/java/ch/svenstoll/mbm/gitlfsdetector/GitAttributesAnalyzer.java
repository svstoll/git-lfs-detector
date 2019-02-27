package ch.svenstoll.mbm.gitlfsdetector;

import ch.svenstoll.mbm.gitlfsdetector.utility.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class GitAttributesAnalyzer {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitAttributesAnalyzer.class);

  private final String outputFolderPath;

  public GitAttributesAnalyzer(String outputFolderPath) {
    if (StringUtility.isNullOrEmpty(outputFolderPath)) {
      throw new IllegalArgumentException("The outputFolderPath must not be null or empty.");
    }
    this.outputFolderPath = outputFolderPath;
  }

  public void checkForGitLfsAttributes(String folderName) {
    if (StringUtility.isNullOrEmpty(folderName)) {
      throw new IllegalArgumentException("The folderName must not be null or empty.");
    }

    String resultFolderPath = outputFolderPath + "/" + folderName + "/";
    if (!Files.exists(Paths.get(resultFolderPath))) {
      throw new IllegalStateException("There are no files to analyze.");
    }

    File resultsFile = new File(outputFolderPath + "/results.txt");
    try (OutputStream outputStream = new FileOutputStream(resultsFile,true);
        PrintWriter printWriter = new PrintWriter(outputStream)) {
      printWriter.println("--------------------------------------------------");
      printWriter.println("Results for: " + folderName);
      printWriter.println();

      List<Path> paths = Files.walk(Paths.get(resultFolderPath), 1)
          .filter(path -> path.toString().endsWith("gitattributes"))
          .collect(Collectors.toList());
      int numGitLfsRepositories = 0;
      int numUnityUsages = 0;
      for (Path path : paths) {
        if (!Files.isDirectory(path) && containsGitLfsAttributes(path)) {
          numGitLfsRepositories++;

          if (isUnityTemplate(path)) {
            numUnityUsages++;
          }
          else {
            if (numGitLfsRepositories - numUnityUsages == 1) {
              printWriter.println("Potential none unity usages:");
            }
            printWriter.println("\t" + path);
          }
        }
      }

      printWriter.println();
      printWriter.println("Number of repositories using Git LFS: " + numGitLfsRepositories);
      printWriter.println("Number of unity usages: " + numUnityUsages);
      printWriter.println();
    }
    catch (IOException e) {
      LOGGER.error("Error while analyzing .gitattributes file \"{}\"", resultFolderPath, e);
    }
  }

  private boolean containsGitLfsAttributes(Path path) throws IOException {
    return Files.readAllLines(path).stream()
        .anyMatch(line -> line.toLowerCase().contains("filter=lfs diff=lfs merge=lfs"));
  }

  private boolean isUnityTemplate(Path path) throws IOException {
    return Files.readAllLines(path).stream()
        .anyMatch(line -> line.toLowerCase().contains("unity"));
  }
}
