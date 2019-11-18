package gis.gisdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Created by Ryan on 2018/10/16.
 */

public class popView extends LinearLayout {

    public popView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public popView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0,0);
    }

    public popView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle,0);
    }

    public popView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        init(context, attrs, defStyle, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        LayoutInflater.from(context).inflate(R.layout.poplayout, this, true);

        Button bt = (Button)findViewById(R.id.popbtn);
        bt.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "click pop botton", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
