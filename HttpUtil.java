import com.sun.net.httpserver.HttpExchange;
import java.io.*;import java.net.URLDecoder;import java.nio.charset.StandardCharsets;import java.util.*;

public final class HttpUtil {
    private HttpUtil(){}
    public static Map<String,String> form(HttpExchange e)throws IOException{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> out=new HashMap<>();for(String pair:body.split("&")){String[] p=pair.split("=",2);if(p.length>0)out.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return out;}
    public static String cookie(HttpExchange e,String name){List<String> headers=e.getRequestHeaders().get("Cookie");if(headers==null)return null;for(String h:headers)for(String pair:h.split(";")){String[] p=pair.trim().split("=",2);if(p.length==2&&p[0].equals(name))return p[1];}return null;}
    public static void text(HttpExchange e,int status,String body,String type)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();}
    public static void redirect(HttpExchange e,String location)throws IOException{e.getResponseHeaders().set("Location",location);e.sendResponseHeaders(303,-1);e.close();}
}
