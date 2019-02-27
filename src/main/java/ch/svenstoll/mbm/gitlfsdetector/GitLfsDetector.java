package ch.svenstoll.mbm.gitlfsdetector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GitLfsDetector {

  private static Logger logger;

  public static void main(String[] args) {
    validateArgs(args);

    // The logger is initialized here, because the output location is only known at execution time.
    System.setProperty("GitLfsExtractorOutputFolder", args[0]);
    logger = LoggerFactory.getLogger(GitLfsDetector.class);

    GitAttributesAnalyzer gitAttributesAnalyzer = new GitAttributesAnalyzer(args[0]);

    logger.info("Searching in top 1000 best matches in GitHub code search for Git LFS usage.");
    String bestCodeMatchQuery = "lfs+filter+diff+merge&in:file&filename:.gitattributes&path:/";
    String bestCodeMatchRepositoriesFolderName = "Best Code Match Repositories";
    GitHubMiner miner1 = new GitHubMiner(args[0] + "/" + bestCodeMatchRepositoriesFolderName);
    List<String> bestCodeMatchRepositories = miner1.crawlGitHubCodeSearchForRepositories(bestCodeMatchQuery);
    miner1.retrieveGitAttributesFilesFromGitHub(bestCodeMatchRepositories);
    gitAttributesAnalyzer.checkForGitLfsAttributes(bestCodeMatchRepositoriesFolderName);

    logger.info("Searching in top 1000 GitHub repositories for Git LFS usage.");
    String topRepositoriesQuery = "stars:>1000&sort=stars&order=desc";
    String topRepositoriesFolderName = "Top Repositories";
    GitHubMiner miner2 = new GitHubMiner(args[0] + "/" + topRepositoriesFolderName);
    List<String> topRepositories = miner2.searchRepositoriesWithGitHubApi(topRepositoriesQuery);
    miner2.retrieveGitAttributesFilesFromGitHub(topRepositories);
    gitAttributesAnalyzer.checkForGitLfsAttributes(topRepositoriesFolderName);
    
    logger.info("Searching in top 1000 Java GitHub repositories for Git LFS usage.");
    String topJavaRepositoriesQuery = "language:java&stars:>1000&sort=stars&order=desc";
    String topJavaRepositoriesFolderName = "Top Java Repositories";
    GitHubMiner miner3 = new GitHubMiner(args[0] + "/" + topJavaRepositoriesFolderName);
    List<String> topJavaRepositories = miner3.searchRepositoriesWithGitHubApi(topJavaRepositoriesQuery);
    miner3.retrieveGitAttributesFilesFromGitHub(topJavaRepositories);
    gitAttributesAnalyzer.checkForGitLfsAttributes(topJavaRepositoriesFolderName);

    logger.info("The analysis has finished. Check \"{}\" for the log file and the results.", args[0]);
  }

  private static void validateArgs(String[] args) {
    if (args.length != 1) {
      System.out.println("Make sure to run this program with the following arguments:");
      System.out.println("[1] Path to the desired output folder");
      System.exit(1);
    }

    try {
      Path outputFolderPath = Paths.get(args[0]);
      if (Files.exists(outputFolderPath) && !Files.isDirectory(outputFolderPath)) {
        System.out.println("The specified output folder is an already existing file.");
        System.exit(1);
      }
    }
    catch (InvalidPathException e) {
      System.out.println("The specified output folder path is invalid.");
      System.exit(1);
    }
  }
}
