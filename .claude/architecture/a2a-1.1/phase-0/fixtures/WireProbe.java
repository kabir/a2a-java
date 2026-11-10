import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
/** Independent baseline serializer runner. It never writes expectations. */
public class WireProbe {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(args[0]), output=Path.of(args[2]); String version=args[1];
    JsonArray probes=JsonParser.parseString(Files.readString(root.resolve("probes.json"))).getAsJsonArray();
    int count=0;
    for(JsonElement item:probes) {
      JsonObject p=item.getAsJsonObject(); if(!p.get("version").getAsString().equals(version))continue;
      String id=p.get("id").getAsString(), suffix=version.equals("0.3")?"_v0_3":"";
      Class<?> util=Class.forName(version.equals("0.3")?"org.a2aproject.sdk.compat03.json.JsonUtil_v0_3":"org.a2aproject.sdk.jsonrpc.common.json.JsonUtil");
      Class<?> type=Class.forName(p.get("class").getAsString());
      String input=Files.readString(root.resolve(p.get("input").getAsString()));
      try {
        Object value=util.getMethod("fromJson",String.class,Class.class).invoke(null,input,type);
        String json=(String)util.getMethod("toJson",Object.class).invoke(null,value);
        Path dest=output.resolve(id);Files.createDirectories(dest.getParent());
        Files.writeString(Path.of(dest+".json"),json+"\n");count++;
        String protoMethod=p.get("proto_method").getAsString();
        if(!protoMethod.isEmpty()) {
          Class<?> converter=Class.forName("org.a2aproject.sdk."+(version.equals("0.3")?"compat03.":"")+"grpc.utils.ProtoUtils"+suffix+"$ToProto");
          Object proto=converter.getMethod(protoMethod,type).invoke(null,value);
          Files.writeString(Path.of(dest+".proto.json"),JsonFormat.printer().print((MessageOrBuilder)proto)+"\n");
          Files.write(Path.of(dest+".pb"),(byte[])proto.getClass().getMethod("toByteArray").invoke(proto));count+=2;
        }
      } catch(InvocationTargetException e) {
        Throwable cause=e.getCause();while(cause.getCause()!=null)cause=cause.getCause();
        JsonObject failure=new JsonObject();failure.addProperty("exception",cause.getClass().getName());failure.addProperty("message",cause.getMessage());
        Path dest=output.resolve(id+".error.json");Files.createDirectories(dest.getParent());Files.writeString(dest,failure+"\n");
        System.out.println("BASELINE FAILURE "+id+": "+failure);count++;
      }
    }
    System.out.println(version+": wrote "+count+" historical wire outputs to "+output);
  }
}
