package gis.hmap;

import android.graphics.drawable.Drawable;

/**
 * Created by Ryan on 2018/9/21.
 */

public class GeneralMarker extends Marker {
    public String imagePath;
    public Drawable image;

    public GeneralMarker(double[] position, String markerId, String image, int width, int height, Object tag) {
        super(position, markerId, width, height, tag);

        this.image = null;
        imagePath = image;
        if (imagePath.startsWith("./"))
            imagePath = Common.getHost() + "/gis/" + image.substring(2);
    }

    public GeneralMarker(double[] position, String markerId, Drawable image, int width, int height, Object tag) {
        super(position, markerId, width, height, tag);

        this.image = image;
        imagePath = null;
    }
}

