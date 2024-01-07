package org.CrunchyCrawler;

import org.json.JSONArray;
import org.json.JSONObject;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CrunchyCrawler
{
    private static final long DELAY_BETWEEN_SELECTIONS = 2000; // 2 seconds API restriction

    public static void main(String[] args) {

        System.out.println("Welcome to the CrunchyCrawler");
        System.out.println("This programme only works with Chrome. Please close your browser now!!");
        System.out.println("------------------------------------------------------------");

        List<String[]> animeDataArray = crawler(); //Get all Animes from Crunchyroll
        List<String[]> highestEpisodes = getHighestEpisodes(animeDataArray); //Get the highest episodes in an anime and series.

        List<String[]> lostAnimes = new ArrayList<>(); //Save anime that can be recognised

        Document doc = createXML();
        Element myAnimeListElement = doc.getDocumentElement();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Specify the storage location for the XML file ( e.g. C:\\save\\the\\file\\here):");
        String filePath = scanner.nextLine();

        System.out.println("\nMyAnimeList will now make suggestions that can be assigned to the title. Select the correct one so that the ID can be assigned:");

        for (String[] animeEntry : highestEpisodes) {
            String animeName = animeEntry[0];
            String season = animeEntry[1];
            String highestEpisode = animeEntry[2];

            System.out.println("-----------------------------------------------------------");
            System.out.println("Anime: " + animeName + ", Season: " + season + ", Highest episode: " + highestEpisode);

            try {
                Thread.sleep(DELAY_BETWEEN_SELECTIONS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            List<Map<String, Object>> listOfMaps = getIDofAnime(season, animeName);
            int selectedMalId = -1;
            if (listOfMaps != null && !listOfMaps.isEmpty()) {
                System.out.println("Select an element:");

                boolean validChoice = false;
                int userChoice = -1;

                while (!validChoice) {
                    for (int i = 0; i < listOfMaps.size(); i++) {
                        Map<String, Object> element = listOfMaps.get(i);
                        String animeName2 = (String) element.get("animeName2");
                        System.out.println((i + 1) + " = Anime Name: " + animeName2);
                    }

                    Scanner scanner2 = new Scanner(System.in);
                    System.out.println("Please select a number for the desired element: (If it is not included, please select (0)");

                    userChoice = scanner2.nextInt();

                    if (userChoice >= 0 && userChoice <= listOfMaps.size()) {
                        validChoice = true;
                    } else {
                        System.out.println("Invalid selection. Please enter a number between 0 and " + listOfMaps.size());
                    }
                }

                if(userChoice == 0){
                    System.out.println("The element is output separately at the end so that you can add it yourself ^^");
                    lostAnimes.add(new String[]{animeName, season, highestEpisode});
                    continue;
                }

                Map<String, Object> selectedElement = listOfMaps.get(userChoice - 1);

                selectedMalId = (int) selectedElement.get("malId");

                if(selectedMalId == -1){
                    System.out.println("Could not find the malID");
                    break;
                }

                saveIntoXML(doc, myAnimeListElement, String.valueOf(selectedMalId), highestEpisode, filePath);
                saveToTXTFile("found", String.valueOf(selectedMalId), animeName, season, highestEpisode, filePath);
            }
            else{
                System.out.println("Error finding ID from anime, add to final list...");
                lostAnimes.add(new String[]{animeName, season, highestEpisode});
                saveToTXTFile("not-found", String.valueOf(selectedMalId), animeName, season, highestEpisode, filePath);
            }
        }
        System.out.println("Everything saved!!");
        System.out.println("----------------------------------------------------------------");
        System.out.println("Here are your anime that you didn't want to/couldn't save:");

        for (String[] animeEntry : lostAnimes) {
            String animeName = animeEntry[0];
            String season = animeEntry[1];
            String highestEpisode = animeEntry[2];

            System.out.println("-----------------------------------------------------------");
            System.out.println("Anime: " + animeName + ", Season: " + season + ", Highest Episode: " + highestEpisode);
        }
    }

    public static List<Map<String, Object>> getIDofAnime(String season, String animeNameRaw) {
        String animeName = animeNameRaw.replaceAll("[^a-zA-Z0-9]", "");
        animeName = animeNameRaw.replace(" ", "%");
        String apiUrl = "https://api.jikan.moe/v4/anime?q=" + animeName  +"%" +season;

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String jsonResponse = response.toString();

            JSONObject jsonObject = new JSONObject(jsonResponse);

            JSONArray dataArray = jsonObject.getJSONArray("data");

            List<Map<String, Object>> listOfMaps = new ArrayList<>();

            int numberOfElementsToRetrieve = Math.min(5, dataArray.length());
            for (int i = 0; i < numberOfElementsToRetrieve; i++) {
                JSONObject dataObject = dataArray.getJSONObject(i);

                int malId = dataObject.getInt("mal_id");
                String animeName2 = dataObject.getString("title");

                Map<String, Object> elementMap = new HashMap<>();
                elementMap.put("malId", malId);
                elementMap.put("animeName2", animeName2);

                listOfMaps.add(elementMap);
            }
            return listOfMaps;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<String[]> crawler (){

        String BASE_URL = "https://sso.crunchyroll.com/login";
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("useAutomationExtension", false);

        Scanner scanner3 = new Scanner(System.in);
        System.out.println("Specify the 'user-data-dir' folder ( e.g. C:\\Users\\'username'\\AppData\\local\\Google\\Chrome\\User Data):");
        options.addArguments("user-data-dir=" + scanner3.nextLine());

        WebDriver browser = new ChromeDriver(options);
        browser.get(BASE_URL);
        List<String[]> animeDataArray = new ArrayList<>();

        try {
            browser.get("https://sso.crunchyroll.com/login");

            Thread.sleep(2000);

            Scanner scanner4 = new Scanner(System.in);
            System.out.println("Please log in in the window and then press enter here:");
            options.addArguments("user-data-dir=" + scanner4.nextLine());

            System.out.println("Press 'Enter' when you have been redirected from Crunchyroll and are logged in");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            browser.get("https://www.crunchyroll.com/de/history");
            Thread.sleep(5000);

            System.out.println("Start process... please do nothing");
            JavascriptExecutor js = (JavascriptExecutor) browser;

            int scrollAmount = 1500;
            int counter = 0;
            while (true) {
                if(counter != 0){
                    long currentHeight = (long) js.executeScript("return Math.max( document.body.scrollHeight, document.body.offsetHeight, document.documentElement.clientHeight, document.documentElement.scrollHeight, document.documentElement.offsetHeight );");

                    js.executeScript("window.scrollBy(0, " + scrollAmount + ");");

                    Thread.sleep(5000);

                    long newHeight = (long) js.executeScript("return Math.max( document.body.scrollHeight, document.body.offsetHeight, document.documentElement.clientHeight, document.documentElement.scrollHeight, document.documentElement.offsetHeight );");

                    if (newHeight == currentHeight) {
                        break;
                    }
                }
                counter ++;

                WebElement element2 = browser.findElement(By.id("content"));

                List<WebElement> elements = element2.findElements(By.cssSelector("div.erc-my-lists-item"));

                for (WebElement element : elements) {
                    WebElement topElement = element.findElement(By.cssSelector("div.history-playable-card__body--lxFhG"));

                    String animename = topElement.findElement(By.tagName("small")).getText();
                    String title = element.findElement(By.tagName("h4")).getText();

                    String[] titleParts = title.split(" - ");

                    if (titleParts.length == 2) {
                        String[] seasonEpisodeParts = titleParts[0].split("\\s");
                        if (seasonEpisodeParts.length == 2) {
                            String season = seasonEpisodeParts[0].substring(1); // Remove"S"
                            String episode = seasonEpisodeParts[1].substring(1); // Remove "E"

                            String episodeTitle = titleParts[1];

                            boolean alreadyExists = animeDataArray.stream()
                                    .anyMatch(entry -> entry[0].equals(animename) && entry[1].equals(season) && entry[2].equals(episode));

                            if (!alreadyExists) {
                                String[] animeEntry = {animename, season, episode, episodeTitle};
                                animeDataArray.add(animeEntry);
                            }
                        }
                    }
                }
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            browser.quit();
        }
        return animeDataArray;
    }

    public static List<String[]> getHighestEpisodes(List<String[]> animeDataArray) {
        List<String[]> highestEpisodes = new ArrayList<>();

        for (String[] animeEntry : animeDataArray) {
            String animeName = animeEntry[0];
            String season = animeEntry[1];
            String episode = animeEntry[2];

            boolean found = false;

            for (String[] highestEntry : highestEpisodes) {
                String currentAnimeName = highestEntry[0];
                String currentSeason = highestEntry[1];

                if (currentAnimeName.equals(animeName) && currentSeason.equals(season)) {
                    found = true;
                    try {
                        float currentHighestEpisode = Float.parseFloat(highestEntry[2]);
                        float currentEpisode = Float.parseFloat(episode);

                        if (currentEpisode > currentHighestEpisode) {
                            highestEntry[2] = String.valueOf(currentEpisode);
                        }
                    } catch (Exception e){

                    }
                }
            }

            if (!found) {
                highestEpisodes.add(new String[]{animeName, season, episode});
            }
        }

        return highestEpisodes;
    }

    public static Document createXML(){
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.newDocument();

            Element myAnimeListElement = doc.createElement("myanimelist");
            doc.appendChild(myAnimeListElement);

            return doc;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void saveIntoXML(Document doc, Element myAnimeListElement, String malID, String episodes, String filePath){
        try {
            Element animeElement1 = createAnimeElement(doc, malID, episodes);
            myAnimeListElement.appendChild(animeElement1);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(doc);

            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            try (FileWriter fileWriter = new FileWriter(filePath + "\\animeList.xml")) {
                fileWriter.write(writer.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static Element createAnimeElement(Document doc, String animedbId, String watchedEpisodes) {
        Element animeElement = doc.createElement("anime");

        // Add other elements with fixed values
        animeElement.appendChild(createElementWithTextContent(doc, "series_animedb_id", animedbId));
        animeElement.appendChild(createElementWithTextContent(doc, "my_id", "0"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_start_date", "0000-00-00"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_finish_date", "0000-00-00"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_score", "0"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_status", "Completed"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_times_watched", "0"));
        animeElement.appendChild(createElementWithTextContent(doc, "my_watched_episodes", watchedEpisodes));

        return animeElement;
    }

    private static Element createElementWithTextContent(Document doc, String elementName, String textContent) {
        Element element = doc.createElement(elementName);
        element.appendChild(doc.createTextNode(textContent));
        return element;
    }

    private static void saveToTXTFile(String type, String malID, String animeName, String season, String episodes, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath + "\\rawList.txt", true))) {
            if (!fileExists(filePath + "\\rawList.txt")) {
                writer.write("Type,MalID,AnimeName,Season,Episodes\n");
            }
            writer.write(String.format("Type: %s, MAL_ID: %s, Anime: %s, Season: %s, Max Episode: %s\n", type, malID, animeName, season, episodes));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean fileExists(String filePath) {
        return new File(filePath).exists();
    }
}

