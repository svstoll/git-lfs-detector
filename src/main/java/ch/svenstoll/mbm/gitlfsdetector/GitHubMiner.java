package ch.svenstoll.mbm.gitlfsdetector;

import ch.svenstoll.mbm.gitlfsdetector.utility.CollectionUtility;
import ch.svenstoll.mbm.gitlfsdetector.utility.StringUtility;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class GitHubMiner {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitHubMiner.class);

  private final String outputFolderPath;

  public GitHubMiner(String outputFolderPath) {
    if (StringUtility.isNullOrEmpty(outputFolderPath)) {
      throw new IllegalArgumentException("The outputFolderPath must not be null or empty.");
    }
    this.outputFolderPath = outputFolderPath;
    createFolderIfNotExists(outputFolderPath);
  }

  public void retrieveGitAttributesFilesFromGitHub(List<String> repositories) {
    if (CollectionUtility.isNullOrEmpty(repositories)) {
      return;
    }

    for (String repository : repositories) {
      try {
        URL url = new URL("https://raw.githubusercontent.com/" + repository + "/master/.gitattributes");
        File gitAttributesFile = new File(outputFolderPath + "/"
            + repository.replace("/", "_") + "_gitattributes");
        FileUtils.copyURLToFile(url, gitAttributesFile);
        FileUtils.touch(gitAttributesFile);
      }
      catch (IOException e) {
        LOGGER.info("No .gitattributes file found for {}.", repository);
      }
    }
  }

  public List<String> searchRepositoriesWithGitHubApi(String query) {
    if (StringUtility.isNullOrEmpty(query)) {
      return Collections.emptyList();
    }

    String miningDataFolderPath = outputFolderPath + "/Mining Data";
    createFolderIfNotExists(miningDataFolderPath);

    List<String> repositories = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      File result = new File(miningDataFolderPath + "/repositories-" + System.currentTimeMillis() + ".json");
      try {
        URL url = new URL("https://api.github.com/search/repositories?q=" + query + "&per_page=100&page=" + i);
        LOGGER.info("Querying {}", url);
        FileUtils.copyURLToFile(url, result);
      }
      catch (IOException e) {
        LOGGER.error("Error while retrieving search results from GitHub API.", e);
      }

      try (FileInputStream inputStream = new FileInputStream(result)) {
        JsonReader reader = Json.createReader(inputStream);
        JsonObject jsonObject = reader.readObject();
        if (jsonObject.isEmpty()) {
          LOGGER.info("Less than 1000 search results.");
          break;
        }

        List<JsonObject> repos = jsonObject
            .getJsonArray("items")
            .getValuesAs(JsonObject.class);
        repos.forEach(item -> repositories.add(item.get("full_name").toString().replace("\"", "")));
      }
      catch (IOException e) {
        LOGGER.error("Error while extracting repository names from JSON response.", e);
      }
    }

    return repositories;
  }

  public List<String> crawlGitHubCodeSearchForRepositories(String query) {
    if (StringUtility.isNullOrEmpty(query)) {
      return Collections.emptyList();
    }

    String miningDataFolderPath = outputFolderPath + "/Mining Data";
    createFolderIfNotExists(miningDataFolderPath);

    WebClient webClient = new WebClient();
    webClient.getOptions().setJavaScriptEnabled(true);
    webClient.getOptions().setCssEnabled(false);

    // Using set because GitHub code search can return the same repository multiple times.
    Set<String> repositories = new HashSet<>();
    try {
      // The GitHub code search can only be used when logged in. It returns at most 1000 results (10 per page).
      loginToGitHub(webClient);
      for (int i = 1; i <= 100; i++) {
        String url = "https://github.com/search?&q=" + query + "&type=Code&p=" + i;
        LOGGER.info("Visiting {}", url);

        HtmlPage codeSearchPage = webClient.getPage(url);
        // The code repository names should be in an anchor element with the CSS class "text-bold".
        String xPathExpression = "//a[contains(concat(\" \", normalize-space(@class), \" \"), \" text-bold \")]";
        List<?> htmlAnchors = codeSearchPage.getByXPath(xPathExpression);
        if (htmlAnchors.isEmpty()) {
          LOGGER.info("No new search results found.");
          break;
        }
        for (Object anchor : htmlAnchors) {
          if (anchor instanceof HtmlAnchor) {
            String projectName = ((HtmlAnchor) anchor).getTextContent();
            if (projectName != null) {
              repositories.add(projectName);
            }
          }
        }

        // Save page for documentation.
        Files.write(
            Paths.get(miningDataFolderPath + "/code_search_page_" + System.currentTimeMillis() + ".html"),
            codeSearchPage.getWebResponse().getContentAsString().getBytes());

        // GitHub is quite restrictive with the amount of allowed requests per minute to the search page.
        Thread.sleep(2000);
      }
    }
    catch (IOException | InterruptedException e) {
      LOGGER.error("Error while crawling GitHub code search", e);
    }

    LOGGER.info("{} repositories using GitHub code search found.", repositories.size());
    return new ArrayList<>(repositories);
  }

  private void createFolderIfNotExists(String resultFolderPath) {
    if (!Files.exists(Paths.get(resultFolderPath))) {
      File resultFolder = new File(resultFolderPath);
      resultFolder.mkdirs();
    }
  }

  private void loginToGitHub(WebClient webClient) throws IOException {
    System.out.println("A login to GitHub is necessary to extract the results when using the " +
        "web interface of GitHub's Search API.");
    String username = promptForGitHubUsername();
    char[] password = promptForGitHubPassword();

    HtmlPage loginPage = webClient.getPage("https://github.com/login");

    HtmlTextInput loginNameField = (HtmlTextInput) loginPage.getElementById("login_field");
    loginNameField.type(username);

    HtmlPasswordInput passwordField = loginPage.getElementByName("password");
    for (char c : password) {
      passwordField.type(c);
    }

    HtmlSubmitInput submitButton = loginPage.getElementByName("commit");
    HtmlPage resultPage = submitButton.click();

    if (resultPage.getUrl().toString().toLowerCase().contains("login")) {
      System.out.println("Could not login");
      LOGGER.error("Could not login to GitHub.");
      System.exit(1);
    }
    LOGGER.info("Login to GitHub successful.");
  }

  private String promptForGitHubUsername() {
    String prompt = "Enter your GitHub username: ";
    if (System.console() != null) {
      return System.console().readLine(prompt);
    }
    else {
      System.out.println(prompt);
      return new Scanner(System.in).nextLine();
    }
  }

  private char[] promptForGitHubPassword() {
    String prompt = "Enter your GitHub password: ";
    if (System.console() != null) {
      return System.console().readPassword(prompt);
    }
    else {
      System.out.println(prompt);
      return new Scanner(System.in).nextLine().toCharArray();
    }
  }
}
