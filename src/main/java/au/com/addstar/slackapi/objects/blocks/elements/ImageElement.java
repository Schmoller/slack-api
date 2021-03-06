package au.com.addstar.slackapi.objects.blocks.elements;

import java.net.MalformedURLException;
import java.net.URL;

import au.com.addstar.slackapi.internal.Utilities;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created for use for the Add5tar MC Minecraft server
 * Created by benjamincharlton on 20/02/2019.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class ImageElement extends Element {
    
    private URL imageURL;
    private String altText;

    public ImageElement() {
        setType(ElementType.IMAGE);
    }

    @Override
    protected void load(JsonObject root, JsonDeserializationContext context) {
        super.load(root, context);
        try {
            this.imageURL = new URL(root.get("imageURL").getAsString());
        } catch (MalformedURLException e) {
            throw new JsonParseException("URL could not be decoded");
        }
        altText = Utilities.getAsString(root.get("alt_text"));
    }
    
    @Override
    protected JsonObject save(JsonObject root, JsonSerializationContext context) {
        super.save(root, context);
        if(this.imageURL != null) {
            root.addProperty("imageUrl",this.imageURL.toString());
        }
        if(altText !=null) {
            root.addProperty("alt_test",altText);
        }
        return root;
    }
}
