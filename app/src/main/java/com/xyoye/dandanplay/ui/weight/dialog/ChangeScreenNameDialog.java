package com.xyoye.dandanplay.ui.weight.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.StringUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.xyoye.dandanplay.R;
import com.xyoye.dandanplay.bean.PersonalBean;
import com.xyoye.dandanplay.bean.event.ChangeScreenNameEvent;
import com.xyoye.dandanplay.utils.AppConfig;
import com.xyoye.dandanplay.utils.net.CommJsonEntity;
import com.xyoye.dandanplay.utils.net.CommJsonObserver;
import com.xyoye.dandanplay.utils.net.NetworkConsumer;

import org.greenrobot.eventbus.EventBus;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by YE on 2018/8/11.
 */


public class ChangeScreenNameDialog extends Dialog {
    @BindView(R.id.user_screen_name_layout)
    TextInputLayout inputLayout;
    @BindView(R.id.user_screen_name_et)
    EditText screenNameEt;
    @BindView(R.id.confirm_bt)
    Button confirmBt;

    public ChangeScreenNameDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_change_screen_name);
        ButterKnife.bind(this);

        confirmBt.setOnClickListener(v -> {
            if (StringUtils.isEmpty(screenNameEt.getText().toString())){
                inputLayout.setErrorEnabled(true);
                inputLayout.setError("昵称不能为空");
            }else {
                String screenName = screenNameEt.getText().toString();
                changeScreenName(screenName);
            }
        });
    }

    private void changeScreenName(String screenName){
        PersonalBean.changeScreenName(screenName, new CommJsonObserver<CommJsonEntity>() {
            @Override
            public void onSuccess(CommJsonEntity commJsonEntity) {
                ToastUtils.showShort("修改昵称成功");
                AppConfig.getInstance().saveUserScreenName(screenName);
                EventBus.getDefault().post(new ChangeScreenNameEvent(screenName));
                ChangeScreenNameDialog.this.cancel();
            }

            @Override
            public void onError(int errorCode, String message) {
                LogUtils.e(message);
                ToastUtils.showShort(message);
            }
        }, new NetworkConsumer());
    }
}