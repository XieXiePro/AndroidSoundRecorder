package pro.xp.com.soundrecorderlib.seleteaudio.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import pro.xp.com.soundrecorderlib.R;

/**
 * @author xiep
 */
@SuppressWarnings("all")
public class ReanameEdtDialog extends Dialog {

    private ReanameEdtDialog(Context context) {
        super(context, R.style.textview_dialog);
    }

    public static class Builder {
        private Context context;
        private String title;
        private String content;
        private String proUint;
        private String positiveButtonText;
        private String negativeButtonText;
        private View contentView;
        private OnClickListener positiveButtonClickListener;
        private OnClickListener negativeButtonClickListener;
        private EditText proNumEt;
        private String proNum;
        private boolean contentVisibility;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(int title) {
            this.title = (String) context.getText(title);
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public String getProNum() {
            return proNumEt.getText().toString().trim();
        }

        public void setProNum(String proNum) {
            this.proNum = proNum;
        }

        public void setProUint(String proUint) {
            this.proUint = proUint;
        }

        private boolean isContentVisibility() {
            return contentVisibility;
        }

        public void setContentVisibility(boolean contentVisibility) {
            this.contentVisibility = contentVisibility;
        }

        public Builder setContentView(View v) {
            this.contentView = v;
            return this;
        }

        public Builder setPositiveButton(int positiveButtonText,
                                         OnClickListener listener) {
            this.positiveButtonText = (String) context
                    .getText(positiveButtonText);
            this.positiveButtonClickListener = listener;
            return this;
        }

        public Builder setPositiveButton(OnClickListener listener) {
            this.positiveButtonText = "取消";
            this.positiveButtonClickListener = listener;
            return this;
        }

        public Builder setNegativeButton(int negativeButtonText,
                                         OnClickListener listener) {
            this.negativeButtonText = (String) context
                    .getText(negativeButtonText);
            this.negativeButtonClickListener = listener;
            return this;
        }

        public Builder setNegativeButton(OnClickListener listener) {
            this.negativeButtonText = "确定";
            this.negativeButtonClickListener = listener;
            return this;
        }

        public ReanameEdtDialog create() {
            final ReanameEdtDialog dialog = new ReanameEdtDialog(context);
            View layout = LayoutInflater.from(context).inflate(R.layout.rename_edt_dialog, null);
            dialog.addContentView(layout, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            ((TextView) layout.findViewById(R.id.feedback_dialog_title_tv))
                    .setText(title);
            ((TextView) layout.findViewById(R.id.feedback_dialog_content_tv))
                    .setText(content);

            proNumEt = ((EditText) layout.findViewById(R.id.rename_item_count));
            proNumEt.setText(proNum);
            proNumEt.setVisibility(View.VISIBLE);
            if (positiveButtonText != null) {
                ((Button) layout.findViewById(R.id.feedback_item_cancel)).setText(positiveButtonText);
                if (positiveButtonClickListener != null) {
                    layout.findViewById(R.id.feedback_item_cancel)
                            .setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    positiveButtonClickListener.onClick(dialog,
                                            DialogInterface.BUTTON_POSITIVE);
                                }
                            });
                }
            } else {
                layout.findViewById(R.id.feedback_item_cancel).setVisibility(
                        View.GONE);
            }
            if (negativeButtonText != null) {
                ((Button) layout.findViewById(R.id.feedback_item_confirm))
                        .setText(negativeButtonText);
                if (negativeButtonClickListener != null) {
                    layout.findViewById(R.id.feedback_item_confirm)
                            .setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    negativeButtonClickListener.onClick(dialog,
                                            DialogInterface.BUTTON_NEGATIVE);
                                }
                            });
                }
            } else {
                layout.findViewById(R.id.feedback_item_confirm).setVisibility(
                        View.GONE);
            }
            if (proUint != null) {
                ((TextView) layout.findViewById(R.id.feedback_item_goodsUnit))
                        .setText(proUint);
            } else if (contentView != null) {
                ((LinearLayout) layout.findViewById(R.id.feedback_dialog_content_ll))
                        .removeAllViews();
                ((LinearLayout) layout.findViewById(R.id.feedback_dialog_content_ll))
                        .addView(contentView, new LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.WRAP_CONTENT));
            }
            if (isContentVisibility()) {
                (layout.findViewById(R.id.feedback_item_goodsUnit))
                        .setVisibility(View.VISIBLE);
            } else {
                (layout.findViewById(R.id.feedback_item_goodsUnit))
                        .setVisibility(View.GONE);
            }
            dialog.setContentView(layout);
            return dialog;
        }
    }
}