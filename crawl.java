import java.net.http.*;
import java.net.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.IOException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

public class crawl {
    // https://www.wikipedia.org/
    // version got to 94 on wikipedia but still takes in bad redirect uri's
    public static final String fileName = "html.txt";

    // validate url
    public static boolean validateURI(String uri) {
        URL url;
        try {
            url = new URL(uri);
        } catch (Exception e1) {
            return false;
        }
        return true;
    }

    public static void printList(ArrayList<String> arrList) { 
        System.out.println("printing urls");
        for (int i = 0; i < arrList.size(); i++) {
            System.out.println(arrList.get(i));
        }
    }

    // return a list of urls from the first successful url from urlsToTry
    public static ArrayList<String> getValidURLS(HttpClient client, ArrayList<String> urlsToTry, Set<String> visited, Set<String> rVisited, int hops) throws IOException, URISyntaxException{
        ArrayList<String> urls = new ArrayList<String>();
        for (int i = 0; i < urlsToTry.size(); i++) {
            // validating url
            String url = urlsToTry.get(i);
            String nullFragment = null;
            URL encodedURL;
            URI uri;
            try {
                encodedURL = new URL(url);
                uri = new URI(encodedURL.getProtocol(), encodedURL.getHost(), encodedURL.getPath(), encodedURL.getQuery(), nullFragment);
                while (!validateURI(url) && i < urlsToTry.size()) {
                    i += 1;
                    url = urlsToTry.get(i);
                    encodedURL = new URL(url);
                    nullFragment = null;
                    uri = new URI(encodedURL.getProtocol(), encodedURL.getHost(), encodedURL.getPath(), encodedURL.getQuery(), nullFragment);
                }
            } catch (URISyntaxException error) {
                System.out.println("bad characters in url");
                continue;
            } catch (MalformedURLException error) {
                System.out.println("invalid url");
                continue;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri.toString()))
                    .build();
            ArrayList<String> blankList = new ArrayList<String>();
            
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                // successful request
                if (response.statusCode() < 300 && response.statusCode() > 199 && !visited.contains(url)) {
                    if (url.charAt(url.length() - 1) == '/' && !visited.contains(url.substring(0, url.length() - 1)) ||
                        url.charAt(url.length() - 1) != '/' && !visited.contains(url)) {
                        visited.add(url);
                        visited.add(url.substring(0, url.length() - 1));
                        System.out.println("[" + (hops + 1) + "] " + url );
                        return getURLSFromBody(response.body());
                        }
                // redirection
                } else if (response.statusCode() >= 300 && response.statusCode() < 400 && !visited.contains(url)) {
                    HttpHeaders headers = response.headers();
                    Map<String, List<String>> headerMap = headers.map();
                    String redirURL = headerMap.get("location").get(0);
                    if (!URI.create(redirURL).isAbsolute()) { // builds abosulte uri if redirect is relative
                        URL encodedURLNoProto = new URL(url);
                        URL encodedURL2 = new URL(encodedURLNoProto.getProtocol() + "://" + URI.create(url).getHost() + redirURL);
                        URI uri2 = new URI(encodedURL2.getProtocol(), encodedURL2.getHost(), encodedURL2.getPath(), encodedURL2.getQuery(), nullFragment);
                        redirURL = uri2.toString();
                    }
                    if (!headerMap.containsKey("location")) { 
                        System.out.println("redirect doesnt doesn't contain location");
                        return blankList;
                    } else if (headerMap.containsKey("location")) {
                        while (response.statusCode() >= 300 && response.statusCode() < 400 && headerMap.containsKey("location")) {
                            HttpRequest redirRequest = HttpRequest.newBuilder()
                                .uri(URI.create(redirURL))
                                .build();
                            response = client.send(redirRequest, BodyHandlers.ofString());
                            headers = response.headers(); 
                            headerMap = headers.map();
                            if (headerMap.containsKey("location")) { // builds abosulte uri if redirect is relative
                                String tempRedirURL = headerMap.get("location").get(0);
                                if (!URI.create(tempRedirURL).isAbsolute()) {
                                    URL encodedURLNoProto3 = new URL(redirURL);
                                    URL encodedURL3 = new URL(encodedURLNoProto3.getProtocol() + "://" + URI.create(redirURL).getHost() + tempRedirURL);
                                    URI uri3 = new URI(encodedURL3.getProtocol(), encodedURL3.getHost(), encodedURL3.getPath(), encodedURL3.getQuery(), nullFragment);
                                    redirURL = uri3.toString();
                                } else {
                                    redirURL = tempRedirURL;
                                }
                            }
                        }
                        if (response.statusCode() < 300) { 
                            if ((url.charAt(url.length() - 1) == '/' && !visited.contains(url.substring(0, url.length() - 1))) || 
                                (url.charAt(url.length() - 1) != '/' && !visited.contains(url))) { // handling trailing backspace
                                visited.add(url);
                                visited.add(url.substring(0, url.length() - 1));
                                System.out.println("[" + (hops + 1) + "] " + url );
                                return getURLSFromBody(response.body());
                            }
                        }
                        if (response.statusCode() >= 400) {
                            System.out.println(response.statusCode() + " moving on");
                        }
                    }
                    // 400 and above codes
                } else if (response.statusCode() >= 400) {
                    System.out.println(response.statusCode() + " moving on");
                }
            } catch (InterruptedException e) {
                System.out.println("Interrupted exception");
                continue;
            } catch (IllegalArgumentException e) {
                System.out.println("invalid argument for request");
                continue;
            }
        }
        return urls;
    }

    // get all urls from respons.body()
    public static ArrayList<String> getURLSFromBody(String responseBody) {
        ArrayList<String> urls = new ArrayList<String>();
        String pattern = "<a(?<=a).*(?=href)href=\"(http[s]?://(.*?))\"";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(responseBody);
        while (m.find()) {
            String foundP = responseBody.substring(m.start(), m.end());
            String pattern2 = "href=\"(http[s]?://(.*?))\"";
            Pattern p2 = Pattern.compile(pattern2);
            Matcher m2 = p2.matcher(foundP);
            if (m2.find()) {
                String foundURL = foundP.substring(m2.start(), m2.end());
                String trimmedP = foundURL.substring(foundURL.indexOf('"') + 1, foundURL.length() - 1);
                urls.add(trimmedP);
            }
        }
        return urls;
    }

    // make hops
    public static void Hop(ArrayList<String> urls, int curHops, int numHops, Set<String> visited, Set<String> rVisited,HttpClient client) throws IOException, URISyntaxException {
        if (urls.size() == 0 && curHops < numHops) { 
            System.out.println("unable to finish hops");
        } else if (curHops - 1 == numHops) {
            System.out.println("done");
        } else {
            urls = getValidURLS(client, urls, visited, rVisited, curHops);
            Hop(urls, curHops + 1, numHops, visited, rVisited, client);
        }
    }
    
    // start hops
    public static void makeHops(String args[]) throws IOException, URISyntaxException {
        if (args.length < 2) {
            System.out.println("must provide a url and hopCount");
            return;
        }
        String url = args[0];
        int numHops = Integer.valueOf(args[1]);

        String pattern = "(http[s]?://(.*?))";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(url);

        if (!m.find()) {
            System.out.println("must provide absoulte url");
            return;
        }
        if (!validateURI(url)) {
            System.out.println("must provide valid url");
            return;
        }
        if (numHops < 1) { 
            System.out.println("must make at least 1 hop");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        Set<String> visited = new HashSet<String>();
        Set<String> rVisited = new HashSet<String>();
        ArrayList<String> firstURL = new ArrayList<String>();
        firstURL.add(url);
        ArrayList<String> urls = getValidURLS(client, firstURL, visited, rVisited, 0);
        if (urls.size() > 0) {
            numHops = numHops - 1;
            Hop(urls, 1, numHops, visited, rVisited, client);
        } else {
            System.out.println("no links available from first link");
        }
    }

    public static void main(String args[]) throws IOException, URISyntaxException {
        makeHops(args);
    }
}
