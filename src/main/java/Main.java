import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.PutObjectResult;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.request.OapiUserGetRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.dingtalk.api.response.OapiUserGetResponse;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.stream.FileImageInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Main {

    private static List<String> fileList = new ArrayList<>();
    private static String endpoint;
    private static String accessKeyId;
    private static String accessKeySecret;
    private static String bucketName;
    private static String dingding_secret;
    private static String dingding_access_token;

    public static void sendDingDing(String content) throws Exception {
        Long timestamp = System.currentTimeMillis();

        String stringToSign = timestamp + "\n" + dingding_secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(dingding_secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");

        String url = "https://oapi.dingtalk.com/robot/send?access_token=" + dingding_access_token + "&timestamp=" + timestamp + "&sign=" + sign;
        System.out.println(url);

        DingTalkClient client = new DefaultDingTalkClient(url);
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("text");
        OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
        text.setContent(content);
        request.setText(text);
        OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();


        List<String> ats = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader("dingding.txt"));

        String str = null;
        while ((str = br.readLine()) != null) {

            str = str.trim();
            if (!str.startsWith("#")) {
                if (!"".equals(str)) {
                    ats.add(str);
                }
            }
        }

        if (ats.size() > 0) {
            at.setAtMobiles(ats);
        } else {
            //isAtAll类型如果不为Boolean，请升级至最新SDK
            at.setIsAtAll(true);
        }

        request.setAt(at);
        OapiRobotSendResponse response = client.execute(request);

        Long errcode = response.getErrcode();
        String errmsg = response.getErrmsg();
        String body = response.getBody();
        String code = response.getCode();

        System.out.println(" errcode : " + errcode);
        System.out.println(" errmsg : " + errmsg);
        System.out.println(" body : " + body);
        System.out.println(" code : " + code);

        if (errcode == 0) {
            System.out.println("通知成功");
        }

    }

    public static void main(String[] args) throws Exception {


        System.out.println("初始化");
        endpoint = System.getenv("ALIYUN_OSS_ENDPOINT");
        accessKeyId = System.getenv("ALIYUN_OSS_ACCESSKEYID");
        accessKeySecret = System.getenv("ALIYUN_OSS_ACCESSKEYSECRET");
        bucketName = System.getenv("ALIYUN_OSS_BUCKETNAME");
        dingding_secret = System.getenv("DINGDING_SECRET");
        dingding_access_token = System.getenv("DINGDING_ACCESS_TOKEN");


        if (endpoint == null || "".equals(endpoint)) {
            System.out.println("请检查环境变量 ALIYUN_OSS_ENDPOINT ");
            return;
        }
        if (accessKeyId == null || "".equals(accessKeyId)) {
            System.out.println("请检查环境变量 ALIYUN_OSS_ACCESSKEYID ");
            return;
        }
        if (accessKeySecret == null || "".equals(accessKeySecret)) {
            System.out.println("请检查环境变量 ALIYUN_OSS_ACCESSKEYSECRET ");
            return;
        }
        if (bucketName == null || "".equals(bucketName)) {
            System.out.println("请检查环境变量  ALIYUN_OSS_BUCKETNAME");
            return;
        }
        if (dingding_secret == null || "".equals(dingding_secret)) {
            System.out.println("请检查环境变量  DINGDING_SECRET");
            return;
        }
        if (dingding_access_token == null || "".equals(dingding_access_token)) {
            System.out.println("请检查环境变量  DINGDING_ACCESS_TOKEN");
            return;
        }

        fileList.clear();

        System.out.println("扫描APK文件");
        search("app/build/outputs/apk");

        System.out.println("准备上传OSS");
        for (String it : fileList) {

            if(it.contains("-release"))
            {
                System.out.println("正在上传 " + it);
                String fileName = it;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//            // 获取文件的后缀名
                String suffixName = fileName.substring(fileName.lastIndexOf("."));
                // 生成上传文件名
                File file = new File(it);

                String finalFileName = file.getName() + new SecureRandom().nextInt(0x0400) + suffixName;
              //  String objectName = sdf.format(new Date()) + "/" + finalFileName;
                String objectName =  file.getName();

                OSSClient ossClient = new OSSClient(endpoint, accessKeyId, accessKeySecret);

                PutObjectResult putObjectResult = ossClient.putObject(bucketName, objectName, file);
                // 设置URL过期时间为24小时。
                Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000 * 24 * 7);
                // 生成以GET方法访问的签名URL，访客可以直接通过浏览器访问相关内容。
                URL url = ossClient.generatePresignedUrl(bucketName, objectName, expiration);


                ossClient.shutdown();
                System.out.println("文件下载地址为 : " + url.toString());

                String s = " \uD83D\uDE19 " + file.getName() + " 打包成功 \n下载地址 : \n " + url.toString().substring(0,url.toString().indexOf("?"));
                System.out.println("通知钉钉机器人");
                sendDingDing(s);
            }


        }
    }

    public static void search(String path) {
        File file = new File(path);
        if (file == null) {
            return;
        }
        File[] files = file.listFiles();
        for (File item : files) {
            if (item.isHidden()) {
                continue;
            }
            if (item.isDirectory()) {
                search(item.getPath());
            }

            if (item.isFile()) {
                if (item.getName().endsWith(".apk")) {
                    fileList.add(item.getPath());
                }
            }
        }
    }
}