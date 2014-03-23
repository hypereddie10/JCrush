package jcrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jcrush.io.ConnectionType;
import jcrush.io.Requester;
import jcrush.model.FileStatus;
import jcrush.model.FileType;
import jcrush.model.MediaCrushFile;
import jcrush.system.Validator;
import jcrush.system.exceptions.FileUploadFailedException;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static jcrush.system.Constants.*;
import static jcrush.system.Utils.*;

/**
 * Static methods that expose the MediaCrush API
 */
public class JCrush {

    private static Gson GSON;
    private static double version = API_VERSION;
    private static final String DEFAULT_SERVER_API_URL = MEDIA_CRUSH_URL + API_DIRECTORY;
    private static String serverApiUrl = DEFAULT_SERVER_API_URL;

    static {
        GSON = new GsonBuilder().setVersion(version).create();
        _setSystemProperties();
    }

    /**
     * Change the server URL where the API resides. <br></br>
     * By default, it uses {@link jcrush.system.Constants#MEDIA_CRUSH_URL} + {@link jcrush.system.Constants#API_DIRECTORY}.
     * @param serverApiUrl The URL where the API waits for connections.
     * @since API v1
     */
    public static void changeApiURL(String serverApiUrl) {
        JCrush.serverApiUrl = serverApiUrl;
    }

    /**
     * Get the current API version being used by {@link com.google.gson.Gson}.
     * @return The current API version as a double
     * @since API v2
     * @see jcrush.JCrush#setAPIVersion(double)
     */
    public static double getAPIVersion() {
        return version;
    }

    /**
     * Set the API version used by {@link com.google.gson.Gson}. While this won't change the endpoints that jCrush uses, this <b>WILL</b> change
     * the fields jCrush deserializes. This can be useful for mobile apps that don't have this extra data implemented yet. <br></br>
     * The current API version is {@link jcrush.system.Constants#API_VERSION}.
     * @param version The version to set it to
     */
    public static void setAPIVersion(double version) {
        JCrush.version = version;
        GSON = new GsonBuilder().setVersion(version).create();
    }

    /**
     * Get the server URL where the API resides. By default, it uses https://www.mediacru.sh/api
     * @return
     *        The URL where the API waits for connections.
     * @since API v1
     */
    public static String getApiURL() {
        return serverApiUrl;
    }

    /**
     * Get the {@link com.google.gson.Gson} object that is used to parse the reposes from MediaCrush
     * @return The {@link com.google.gson.Gson} object used
     * @since API v2
     */
    public static Gson getJsonParser() {
        return GSON;
    }

    private static void _setSystemProperties() {
        System.setProperty("http.agent", DEFAULT_USER_AGENT);
    }

    /**
     * Returns information about the file whose hash is <b>hash</b>
     * @param hash
     *            <b>NOT NULLABLE</b>
     *            <br></br>
     *            - The hash of the file to retrieve.
     * @return
     *        The file represented as a {@link MediaCrushFile} object
     * @throws IOException
     *                    An {@link IOException} can be thrown for the following reasons: <br></br>
     *                    * There was an error invoking {@link jcrush.io.Requester#connect()} <br></br>
     *                    * The json returned contained a 404 error
     * @see jcrush.JCrush#getFile(String) 
     * @since API v2
     * @deprecated This method is not used in API v2. Please use {@link jcrush.JCrush#getFile(String)}
     */
    @Deprecated
    public static MediaCrushFile getFileInfo(String hash) throws IOException {
        return getFile(hash);
    }

    /**
     * Returns an array of {@link MediaCrushFile} containing information about the file whose hash is <b>hash</b>
     * @param hash
     *           <b>NOT NULLABLE</b>
     *           <br></br>
     *           - An array of hash's to lookup
     * @return
     *        An array of files represented as a {@link MediaCrushFile} object
     * @throws IOException
     *                    An {@link IOException} can be thrown for the following reasons: <br></br>
     *                    * There was an error invoking {@link jcrush.io.Requester#connect()} <br></br>
     *                    * The json returned contained a 404 error
     * @see jcrush.io.Requester#connect()
     * @deprecated This method is no longer used in API v2. Please use {@link jcrush.JCrush#getFiles(String...)} instead.
     */
    @Deprecated
    public static MediaCrushFile[] getFileInfos(String... hash) throws IOException {
        return getFiles(hash);
    }

    /**
     * A convenience method. Returns a single {@link MediaCrushFile} object. When the hash specified does not exist, this method simply returns null.
     * @param hash
     *            The hash to retrieve
     * @return
     *        The file represented as a {@link MediaCrushFile} object.
     * @throws IOException
     *                    This exception can be thrown if {@link JCrush#getFileInfo(String)} or {@link JCrush#getFileStatus(String)} throws an exception
     * @see JCrush#getFiles(String...)
     * @since API v2
     */
    public static MediaCrushFile getFile(String hash) throws IOException {
        if (!doesExists(hash))
            return null;

        MediaCrushFile file = getFileInfo(hash);
        FileStatus status = getFileStatus(hash).getStatus();

        try {
            setStatus(file, status);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return file;
    }

    /**
     * Returns an array of {@link MediaCrushFile} objects. If any of the hashes specified does not exist, then that item in the array will simply be null. <br></br>
     * @param hash
     *            The hash(s) to retrieve
     * @return
     *        The file(s) represented as an array of {@link MediaCrushFile} object(s).
     * @throws IOException
     *                    An {@link IOException} can be thrown for the following reasons: <br></br>
     *                    * There was an error invoking {@link jcrush.io.Requester#connect()} <br></br>
     *                    * The json returned contained a 404 error
     * @since API v2
     */
    public static MediaCrushFile[] getFiles(String... hash) throws IOException {
        Validator.validateNotNull(hash, "hash");
        String list = "";
        for (int i = 0; i < hash.length; i++) {
            if (i == 0)
                list = hash[i];
            else
                list += "," + hash[i];
        }

        URL uri = new URL(serverApiUrl + "info?list=" + list);
        Requester requester = new Requester(ConnectionType.GET, uri);
        requester.setRecieve(true);
        requester.connect();

        String json = requester.getResponse();
        requester.disconnect();
        Validator.validateNot404(json);

        MediaCrushFile[] files = new MediaCrushFile[hash.length];
        Map results = GSON.fromJson(json, Map.class);
        for (int i = 0; i < files.length; i++) {
            if (results.containsKey(hash[i])) {
                Object obj = results.get(hash[i]);
                if (obj instanceof MediaCrushFile)
                    files[i] = (MediaCrushFile)obj;
                else files[i] = null;
            } else files[i] = null;
        }

        return files;
    }

    /**
     * A convenience method. Returns whether a hash exists or not. <br></br>
     * This method simply invokes {@link jcrush.JCrush#getFile(String)} and checks to see if the returned value is null or not.
     * @param hash
     *            The hash to lookup
     * @return
     *        true if the has exists or false if it does not
     * @throws IOException
     *                    An {@link IOException} will be thrown if {@link jcrush.JCrush#getFile(String)} raises an exception
     * @see jcrush.JCrush#getFile(String)
     * @since API v2
     */
    public static boolean doesExists(String hash) throws IOException {
        Validator.validateNotNull(hash, "hash");

        URL uri = new URL(serverApiUrl + hash + "/exists");
        Requester requester = new Requester(ConnectionType.HEAD, uri);
        requester.setRecieve(true);
        try {
            requester.connect();
        } catch (FileNotFoundException ignored) {
            requester.disconnect();
            return false;
        }
        requester.disconnect();
        return true;
    }

    /**
     * Upload a file to mediacru.sh <br></br>
     * This method creates a new instance of a {@link File} and then invoke {@link JCrush#uploadFile(java.io.File)}
     * @param filePath
     *                The full file path to the file that will be uploaded to mediacru.sh
     * @throws IOException
     *                    An IOException can be thrown for the following reasons:<br></br>
     *                    * The file path specified is not a file, but a directory   <br></br>
     *                    * The file path specified does not exist  <br></br>
     *                    * {@link JCrush#uploadFile(java.io.File)} raises an Exception  <br></br>
     * @see JCrush#uploadFile(java.io.File)
     * @since API v1
     */
    public static void uploadFile(String filePath) throws IOException {
        Validator.validateNotNull(filePath, "filePath");
        File file = new File(filePath);
        if (!file.exists())
            throw new FileNotFoundException("The file could not be found!");
        else if (file.isDirectory())
            throw new IOException("The filePath specified is a directory!");
        else
            uploadFile(file);
    }

    /**
     * Upload the file specified in the parameter to mediacru.sh <br></br>
     * Only the following file types are allowed:<br></br>
     * * .png <br></br>
     * * .jpg <br></br>
     * * .jpeg <br></br>
     * * .gif <br></br>
     * * .mp4 <br></br>
     * * .ogv <br></br>
     * * .mp3 <br></br>
     * * .ogg <br></br>
     * An unknown file type will raise an {@link IOException} <br></br>
     *
     * @param file
     *            The file to upload represented as a {@link File} object
     * @return
     *        The hash of the currently uploading file on mediacru.sh
     * @throws FileUploadFailedException
     *                                  A FileUploadFiledException can be thrown for the following reasons: <br></br>
     *                                  * The file was already uploaded. <br></br>
     *                                  * The rate limit was exceeded. <br></br>
     *                                  * The file extension is not acceptable. However, for this to be thrown is highly unlikely. An {@link IOException} will be thrown first
     *                                  before the upload with the reason "Unknown file type!"
     * @throws IOException
     *                    An IOException can be thrown for the following reasons:<br></br>
     *                    * An unknown file type was specified <br></br>
     *                    * The file specified does not exist <br></br>
     *                    * The file specified is not a file, but a directory <br></br>
     *                    * An unknown error code was returned from the server <br></br>
     * @since API v1
     */
    public static String uploadFile(File file) throws IOException {
        Validator.validateNotNull(file, "file");

        if (!file.exists())
            throw new FileNotFoundException();
        if (file.isDirectory())
            throw new IOException("This file is a directory!");

        //Get content type of file
        FileType contentType = FileType.toFileType(toContentType(file));
        if (contentType == null)
            throw new IOException("Unknown file type!");

        return uploadFile(new FileInputStream(file), contentType, file.getName());
    }

    /**
     * Upload an image/sound/video to mediacru.sh using the data provided in the InputStream provided <br></br>
     * This method requires a file name with an extension at the end. This can be provided manually or you can use the
     * helper method {@link jcrush.model.FileType#getFileExtension()} <br></br>
     * Only the following file types are allowed:<br></br>
     * * .png <br></br>
     * * .jpg <br></br>
     * * .jpeg <br></br>
     * * .gif <br></br>
     * * .mp4 <br></br>
     * * .ogv <br></br>
     * * .mp3 <br></br>
     * * .ogg <br></br>
     * An unknown file type will raise an {@link IOException} <br></br>
     *
     * @param imageData
     *                 The {@link InputStream} with the image data to upload
     * @param type
     *            The {@link FileType} of the data being uploaded
     * @param fileName
     *                 The file name for this data <b>INCLUDING</b> the file extension.
     * @return
     *        The hash of the currently uploading file on mediacru.sh
     * @throws FileUploadFailedException
     *                                  A FileUploadFiledException can be thrown for the following reasons: <br></br>
     *                                  * The file was already uploaded. <br></br>
     *                                  * The rate limit was exceeded. <br></br>
     *                                  * The file extension is not acceptable. However, for this to be thrown is highly unlikely. An {@link IOException} will be thrown first
     *                                  before the upload with the reason "Unknown file type!"
     * @throws IOException
     *                    An IOException can be thrown for the following reasons:<br></br>
     *                    * An unknown file type was specified <br></br>
     *                    * The file specified does not exist <br></br>
     *                    * The file specified is not a file, but a directory <br></br>
     *                    * An unknown error code was returned from the server <br></br>
     * @since API v1
     */
    public static String uploadFile(InputStream imageData, FileType type, String fileName) throws IOException {

        //Read file into byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(imageData, baos, 1024);
        byte[] bytes = baos.toByteArray();

        //Prepare form data to send
        String header = "\r\n--" + CONTENT_DIVIDER + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + "\r\n" +
                "Content-Type: " + type.toString() + "\r\n" +
                "Content-Transfer-Encoding: binary\r\n" +
                "\r\n";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(header.getBytes("ASCII"));
        bos.write(bytes);
        String footer = "\r\n--" + CONTENT_DIVIDER + "--";
        bos.write(footer.getBytes("ASCII"));
        byte[] tosend = bos.toByteArray();

        //Prepare the requester with form data
        URL uri = new URL(serverApiUrl + "upload/file");
        Requester requester = new Requester(ConnectionType.POST, uri);
        requester.setPostData(tosend);
        requester.addHeader("Content-Length", "" + tosend.length);
        requester.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        requester.addHeader("Accept-Encoding", "gzip, deflate");
        requester.addHeader("X-Requested-With", "XMLHttpRequest");
        requester.addHeader("Content-Type", "multipart/form-data; boundary=" + CONTENT_DIVIDER);
        requester.setRecieve(true);
        try {
            requester.connect(); //Connect
        } catch (IOException e) {
            BufferedReader read = new BufferedReader(new InputStreamReader(
                    requester.getErrorStream()));
            StringBuilder builder = new StringBuilder(100);
            String line;
            while ((line = read.readLine()) != null)
                builder.append(line);
            read.close();
            String reason = builder.toString();

            int code = requester.getResponseCode();
            switch (code) {
                case 409:
                    throw new FileUploadFailedException("This file was already uploaded!", e);
                case 420:
                    throw new FileUploadFailedException("The rate limit was exceeded. Enhance your calm.", e);
                case 415:
                    throw new FileUploadFailedException("The file extension is not acceptable.", e);
                default:
                    throw new IOException("The server responded with an unknown error code! (" + code + ")", e);
            }
        }

        //Parse results
        int code = requester.getResponseCode();
        String json = requester.getResponse();
        requester.disconnect(); //Disconnect
        Map map = GSON.fromJson(json, Map.class);
        if (code == 200 && !map.containsKey("error")) {
            return (String)map.get("hash");
        } else {
            if (code == 200) {
                try {
                    code = Integer.parseInt((String)map.get("error"));
                } catch (Throwable t) {
                    throw new IOException("The server responded with an unknown error (" + (map.get("error") == null ? "null" : map.get("error")) + ")", t);
                }
            }
            switch (code) {
                case 409:
                    throw new FileUploadFailedException("This file was already uploaded!");
                case 420:
                    throw new FileUploadFailedException("The rate limit was exceeded. Enhance your calm.");
                case 415:
                    throw new FileUploadFailedException("The file extension is not acceptable.");
                default:
                    throw new IOException("The server responded with an unknown error code! (" + code + ")");
            }
        }
    }

    /**
     * Delete a file from mediacru.sh. <br></br>
     * Only same IP as the uploader may delete the file specified. An {@link IOException} will be thrown if the IP's do
     * not match
     * @param hash
     *            The hash of the file to delete
     * @throws IOException
     *                    An IOException can be thrown for the following reasons: <br></br>
     *                    * The hash specified does not exist  <br></br>
     *                    * The IP does not match the stored hash <br></br>
     *                    * The server responded with an unknown error code. <br></br>
     * @since API v1
     */
    public static void delete(String hash) throws IOException {
        Validator.validateNotNull(hash, "hash");

        URL uri = new URL(serverApiUrl + "files/" + hash);
        Requester requester = new Requester(ConnectionType.DELETE, uri);
        requester.setRecieve(true);
        try {
            requester.connect();
        } catch (FileNotFoundException e) {
            requester.disconnect();
            throw new IOException("There is no file with that hash!");
        }

        int code = requester.getResponseCode();
        requester.disconnect();

        if (code != 200) {
            if (code == 404)
                throw new IOException("There is no file with that hash!");
            else if (code == 401)
                throw new IOException("The IP does not match the stored hash!");
            else
                throw new IOException("The server responded with an unknown code! (" + code + ")");
        }
    }

    /**
     * Delete the {@link MediaCrushFile} object from mediacru.sh <br></br>
     * Only same IP as the uploader may delete the file specified. An {@link IOException} will be thrown if the IP's do
     * not match
     * @param file
     *            The {@link MediaCrushFile} object that represents the file to delete
     * @throws IOException
     *                    An IOException will only be thrown if {@link JCrush#delete(String)} throws an exception
     * @see JCrush#delete(String)
     * @since API v1
     */
    public static void delete(MediaCrushFile file) throws IOException {
        Validator.validateNotNull(file, "file");
        delete(file.getHash());
    }

    /**
     * Get the current upload status for the file specified by the hash
     * @param hash
     *            The hash of the file
     * @return
     *        A {@link MediaCrushFile} object that represents the file. <br></br>
     *        You can use {@link jcrush.model.MediaCrushFile#getStatus()} to get the status of the file
     * @throws IOException
     *                   An IOException can be thrown for the following reasons: <br></br>
     *                   * {@link jcrush.io.Requester#connect()} throws an IOException
     *                   * There was an error constructing the {@link MediaCrushFile} file.
     * @see jcrush.JCrush#getFile(String)
     * @since API v2
     * @deprecated This method is no longer used, because {@link jcrush.JCrush#getFile(String)} provides the file status in the returned object. Please use that method instead.
     */
    @Deprecated
    public static MediaCrushFile getFileStatus(String hash) throws IOException {
        return getFile(hash);
    }
    /**
     * Upload the file at a URL to mediacru.sh <br></br>
     * {@link JCrush#uploadFileViaURL(String)} is invoked with {@link java.net.URL#toString()} passed as the URL parameter
     * @param url
     *          The URL from where to fetch the file to upload, represented as a {@link URL} object
     * @return
     *        The hash of the currently uploading file on mediacru.sh
     * @throws IOException
     *                    An IOException will only be thrown if {@link JCrush#uploadFileViaURL(String)} throws an exception
     * @see JCrush#uploadFileViaURL(String)
     * @since API v1
     */
    public static String uploadFileViaURL(URL url) throws IOException {
        Validator.validateNotNull(url, "url");
        return uploadFileViaURL(url.toString());
    }

    /**
     * Upload the file at a URL to mediacru.sh <br></br>
     * {@link JCrush#uploadFileViaURL(String)} is invoked with {@link java.net.URI#toString()} passed as the URL parameter
     * @param uri
     *          The URL from where to fetch the file to upload, represented as a {@link URI} object
     * @return
     *        The hash of the currently uploading file on mediacru.sh
     * @throws IOException
     *                    An IOException will only be thrown if {@link JCrush#uploadFileViaURL(String)} throws an exception
     * @see JCrush#uploadFileViaURL(String)
     * @since API v1
     */
    public static String uploadFileViaURL(URI uri) throws IOException {
        Validator.validateNotNull(uri, "uri");
        return uploadFileViaURL(uri.toString());
    }

    /**
     * Upload the file at a URL to mediacru.sh <br></br>
     * @param url
     *            The URL from where to fetch the file to upload
     * @return
     *        The hash of the currently uploading file on mediacru.sh
     * @throws FileUploadFailedException
     *                                  A FileUploadFiledException can be thrown for the following reasons: <br></br>
     *                                  * The file was already uploaded. <br></br>
     *                                  * The rate limit was exceeded. <br></br>
     *                                  * The file extension is not acceptable. <br></br>
     *                                  * The URL was invalid <br></br>
     *                                  * The file requested does not exist <br></br>
     * @throws IOException
     *                    An IOException can be thrown for the following reasons:<br></br>
     *                    * An unknown error code was returned from the server <br></br>
     * @since API v1
     */
    public static String uploadFileViaURL(String url) throws IOException {
        Validator.validateNotNull(url, "url");

        String post = "url=" + url;
        URL uri = new URL(serverApiUrl + "upload/url");
        Requester requester = new Requester(ConnectionType.POST, uri);
        requester.setPostData(post);
        requester.addHeader("Content-Length", "" + post.length());
        requester.setRecieve(true);
        try {
            requester.connect(); //Connect
        } catch (IOException e) {
            BufferedReader read = new BufferedReader(new InputStreamReader(
                    requester.getErrorStream()));
            StringBuilder builder = new StringBuilder(100);
            String line;
            while ((line = read.readLine()) != null)
                builder.append(line);
            read.close();
            String reason = builder.toString();

            int code = requester.getResponseCode();
            switch (code) {
                case 400:
                    throw new FileUploadFailedException("The URL is invalid.", e);
                case 404:
                    throw new FileUploadFailedException("The file requested does not exist", e);
                case 409:
                    throw new FileUploadFailedException("This file was already uploaded!", e);
                case 420:
                    throw new FileUploadFailedException("The rate limit was exceeded. Enhance your calm.", e);
                case 415:
                    throw new FileUploadFailedException("The file extension is not acceptable.", e);
                default:
                    throw new IOException("The server responded with an unknown error code! (" + code + ")", e);
            }
        }

        //Parse results
        int code = requester.getResponseCode();
        String json = requester.getResponse();
        requester.disconnect(); //Disconnect
        Map map = GSON.fromJson(json, Map.class);
        if (code == 200 && !map.containsKey("error")) {
            return (String)map.get("hash");
        } else {
            if (code == 200) {
                try {
                    code = Integer.parseInt((String)map.get("error"));
                } catch (Throwable t) {
                    throw new IOException("The server responded with an unknown error (" + (map.get("error") == null ? "null" : map.get("error")) + ")", t);
                }
            }
            switch (code) {
                case 400:
                    throw new FileUploadFailedException("The URL is invalid.");
                case 404:
                    throw new FileUploadFailedException("The file requested does not exist");
                case 409:
                    throw new FileUploadFailedException("This file was already uploaded!");
                case 420:
                    throw new FileUploadFailedException("The rate limit was exceeded. Enhance your calm.");
                case 415:
                    throw new FileUploadFailedException("The file extension is not acceptable.");
                default:
                    throw new IOException("The server responded with an unknown error code! (" + code + ")");
            }
        }
    }
}
