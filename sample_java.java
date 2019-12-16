import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import com.fasterxml.jackson.databind.ObjectMapper;

class authInfo {
    //認証情報定義
    public static String clientId = "input client id here";
    public static String clientSecret = "input client secret here";
}

class apiUrl{
    //接続先定義
    public static String oauthUrl = "https://api.ce-cotoha.com/v1/oauth/accesstokens";
    public static String ttsUrl = "https://api.ce-cotoha.com/api/tts/v1/tts";
}

class tokenResponse{
    public String access_token;
    public String token_type;
    public String expires_in;
    public String issued_at;
    public String scope;
}

class tokenErrorResponse{
    public Object result;
    public String message;
    public int status;
}

class errorResponse{
    public String code;
    public String detail;
}

public class sample_java{

    // main の処理
    // コマンドライン引数1 : 音声合成設定を記入したjsonファイル
    // コマンドライン引数2(option) : 出力wavファイル名
    // 出力 : 合成音声wavファイル
    public static void main(String[] args) {
        if(args.length <= 0){
            System.out.println("usage: java sample_java [input_json_file] [(option)output_wav_file]");
            System.exit(1);
        }
        File inputFilePath = new File(args[0]);
        File outputFilePath;
        if(args.length > 1){
            outputFilePath = new File(args[1]);
        }
        else{
            outputFilePath = new File("output.wav");
        }
        String accessToken = getToken(apiUrl.oauthUrl, authInfo.clientId, authInfo.clientSecret);
        String postData = inputFromFile(inputFilePath);
        byte[] audioData = postAndRecieve(apiUrl.ttsUrl, accessToken, postData);
        outputToFile(audioData, outputFilePath);
    }

    // アクセストークン取得
    public static String getToken(String postUrl, String clientId, String clientSecret) {
        HttpURLConnection connection = null;
        String accessToken = "";
        String requestData = "{\n"
        		+ "\"clientId\":\"" + clientId + "\",\n"
        		+ "\"clientSecret\":\"" + clientSecret + "\",\n"
                + "\"grantType\":\"client_credentials\"\n"
                + "}";
        try {
            String address = postUrl;
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Content-Length", Integer.toString(requestData.length()));
            connection.connect();

            PrintStream ps = new PrintStream(connection.getOutputStream(), true, "UTF-8");
            ps.print(requestData);
            ps.close();

            int responseCode = connection.getResponseCode();

            if(responseCode!=201){
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                StringBuffer tempJSON = new StringBuffer();
                String inputLine;
                while ((inputLine = br.readLine()) != null ){
                    tempJSON.append(inputLine);
                }
                br.close();

                String resultJSON;
                resultJSON = tempJSON.toString();
                ObjectMapper mapper = new ObjectMapper();
                tokenErrorResponse ter = mapper.readValue(resultJSON,tokenErrorResponse.class);
                System.out.println("[ERROR!(@getToken)] status: " + ter.status + ", message: " + ter.message);
                connection.disconnect();
                System.exit(1);
            }
            else{
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuffer tempJSON = new StringBuffer();
                String inputLine;
                while ((inputLine = br.readLine()) != null ){
                    tempJSON.append(inputLine);
                }
                br.close();

                String resultJSON;
                resultJSON = tempJSON.toString();
                ObjectMapper mapper = new ObjectMapper();
                tokenResponse tr = mapper.readValue(resultJSON,tokenResponse.class);
                accessToken = tr.access_token;
                connection.disconnect();
                System.out.println("getToken completed successfully."); 
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return accessToken;
    }

    // JSON ファイルの読み込み
    public static String inputFromFile(File f) {
        StringBuilder data = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));
            String str;
            while (( str = br.readLine()) != null) {
                data.append(str);
            }
            br.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("inputFromFile completed successfully.");
        System.out.println("post data: " + data); 
        
        return data.toString();
    }

    // データをポストし合成音声を取得
    public static byte[] postAndRecieve(String postUrl, String accessToken, String requestData) {
        ByteArrayOutputStream responseData = new ByteArrayOutputStream();
        HttpURLConnection connection = null;
        try {
            String address = postUrl;
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "audio/wav");
            connection.setRequestProperty("Content-Length", Integer.toString(requestData.length()));
            connection.setRequestProperty("Authorization", "Bearer "+accessToken);
            connection.connect();
            PrintStream ps = new PrintStream(connection.getOutputStream(), true, "UTF-8");
            ps.print(requestData);
            ps.close();

            int responseCode = connection.getResponseCode();

            if(responseCode!=200){
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                StringBuffer tempJSON = new StringBuffer();
                String inputLine;
                while ((inputLine = br.readLine()) != null ){
                    tempJSON.append(inputLine);
                }
                br.close();
    
                String resultJSON;
                resultJSON = tempJSON.toString();
                ObjectMapper mapper = new ObjectMapper();
                errorResponse er = mapper.readValue(resultJSON,errorResponse.class);
                System.out.println("[ERROR!(@postAndRecieve)] status: " + responseCode + ", code: " + er.code + ", detail: " + er.detail);
                connection.disconnect();
                System.exit(1);
            }
            else{
                BufferedInputStream bis = new BufferedInputStream(connection.getInputStream(), 10000000);
                String date = connection.getHeaderField("Date");
                String contentType = connection.getHeaderField("Content-Type");
                byte[] buf = new byte[882000];
                int n;
                while ((n = bis.read(buf, 0, buf.length)) != -1) {
                    responseData.write(buf, 0, n);
                }
                bis.close();
                connection.disconnect();
                System.out.println("postAndRecieve completed successfully."); 
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return responseData.toByteArray();
    }

    // 音声ファイルを出力
    public static void outputToFile(byte[] data, File outputFilePath) {
        FileOutputStream outputData = null;
        try {
            outputData = new FileOutputStream(outputFilePath);
            outputData.write(data);
            outputData.flush();
            outputData.close();
            System.out.println("outputToFile completed successfully.");
            System.out.println(outputFilePath + " has been generated.");
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}