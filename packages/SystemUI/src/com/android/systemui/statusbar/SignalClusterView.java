/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;

import java.util.ArrayList;
import java.util.List;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCluster,
        SecurityController.SecurityControllerCallback {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final int STATUS_BAR_STYLE_ANDROID_DEFAULT = 0;
    private final int STATUS_BAR_STYLE_CDMA_1X_COMBINED = 1;
    private final int STATUS_BAR_STYLE_DEFAULT_DATA = 2;
    private final int STATUS_BAR_STYLE_DATA_VOICE = 3;

    private int mStyle = 0;
    private int[] mShowTwoBars;

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mNoSimsVisible = false;
    private boolean mVpnVisible = false;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mAirplaneContentDescription;
    private String mWifiDescription;
    private ArrayList<PhoneState> mPhoneStates = new ArrayList<PhoneState>();

    ViewGroup mWifiGroup;
    ImageView mVpn, mWifi, mAirplane, mNoSims;
    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;
    LinearLayout mMobileSignalGroup;

    private int mWideTypeIconStartPadding;
    private int mSecondaryTelephonyPadding;
    private int mEndPadding;
    private int mEndPaddingNothingVisible;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mShowNetworkActivity = context.getResources().getBoolean(R.bool.config_ShowNetworkActivity);
        mStyle = context.getResources().getInteger(R.integer.status_bar_style);
        mShowTwoBars = context.getResources().getIntArray(
                R.array.config_showVoiceAndDataForSub);
    }

    public void setNetworkController(NetworkControllerImpl nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    public void setSecurityController(SecurityController sc) {
        if (DEBUG) Log.d(TAG, "SecurityController=" + sc);
        mSC = sc;
        mSC.addCallback(this);
        mVpnVisible = mSC.isVpnEnabled();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWideTypeIconStartPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding);
        mSecondaryTelephonyPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.secondary_telephony_padding);
        mEndPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.signal_cluster_battery_padding);
        mEndPaddingNothingVisible = getContext().getResources().getDimensionPixelSize(
                R.dimen.no_signal_cluster_battery_padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mNoSims         = (ImageView) findViewById(R.id.no_sims);
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);
        mMobileSignalGroup = (LinearLayout) findViewById(R.id.mobile_signal_group);
        for (PhoneState state : mPhoneStates) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mWifiGroup      = null;
        mWifi           = null;
        mAirplane       = null;
        mMobileSignalGroup.removeAllViews();
        mMobileSignalGroup = null;

        super.onDetachedFromWindow();
    }

    // From SecurityController.
    @Override
    public void onStateChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                mVpnVisible = mSC.isVpnEnabled();
                apply();
            }
        });
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        if (mShowNetworkActivity) {
            mWifiActivityId = activityIcon;
        }
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int typeIcon,
            String contentDescription, String typeContentDescription, boolean isTypeIconWide,
            int subId) {
        PhoneState state = getOrInflateState(subId);
        state.mMobileVisible = visible;
        state.mMobileStrengthId = strengthIcon;
        state.mMobileTypeId = typeIcon;
        state.mMobileDescription = contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = isTypeIconWide;

        apply();
    }

    @Override
    public void setNoSims(boolean show) {
        mNoSimsVisible = show;
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        // Clear out all old subIds.
        mPhoneStates.clear();
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        final int n = subs.size();
        for (int i = 0; i < n; i++) {
            inflatePhoneState(subs.get(i).getSubscriptionId());
        }
    }

    private PhoneState getOrInflateState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        return inflatePhoneState(subId);
    }

    private PhoneState inflatePhoneState(int subId) {
        PhoneState state = new PhoneState(subId, mContext);
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        mPhoneStates.add(state);
        return state;
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId, int contentDescription) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;
        mAirplaneContentDescription = contentDescription;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        for (PhoneState state : mPhoneStates) {
            state.populateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }

        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.mMobile.setImageDrawable(null);
            }
            if (state.mMobileType != null) {
                state.mMobileType.setImageDrawable(null);
            }
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        mVpn.setVisibility(mVpnVisible ? View.VISIBLE : View.GONE);
        if (DEBUG) Log.d(TAG, String.format("vpn: %s", mVpnVisible ? "VISIBLE" : "GONE"));
        if (mWifiVisible) {
            mWifi.setImageResource(mWifiStrengthId);
            if (mShowNetworkActivity) {
                mWifiActivity.setImageResource(mWifiActivityId);
            }
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        boolean anyMobileVisible = false;
        int firstMobileTypeId = 0;
        for (PhoneState state : mPhoneStates) {
            if (state.apply(anyMobileVisible)) {
                if (!anyMobileVisible) {
                    firstMobileTypeId = state.mMobileTypeId;
                    anyMobileVisible = true;
                }
            }
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setContentDescription(mAirplaneContentDescription != 0 ?
                    mContext.getString(mAirplaneContentDescription) : null);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mWifiVisible && ((mIsAirplaneMode) || (mNoSimIconId != 0))) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (((anyMobileVisible && firstMobileTypeId != 0) || mNoSimsVisible) && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        mNoSims.setVisibility(mNoSimsVisible ? View.VISIBLE : View.GONE);

        boolean anythingVisible = mNoSimsVisible || mWifiVisible || mIsAirplaneMode
                || anyMobileVisible || mVpnVisible;
        setPaddingRelative(0, 0, anythingVisible ? mEndPadding : mEndPaddingNothingVisible, 0);
    }

    private class PhoneState {
        private final int mSubId;
        private boolean mMobileVisible = false;
        private int mMobileStrengthId = 0, mMobileTypeId = 0;
        private boolean mIsMobileTypeIconWide;
        private String mMobileDescription, mMobileTypeDescription;

        private ViewGroup mMobileGroup;
        private ImageView mMobile, mMobileType;

        public PhoneState(int subId, Context context) {
            ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                    .inflate(R.layout.mobile_signal_group, null);
            setViews(root);
            mSubId = subId;
        }

        public void setViews(ViewGroup root) {
            mMobileGroup    = root;
            mMobile         = (ImageView) root.findViewById(R.id.mobile_signal);
            mMobileType     = (ImageView) root.findViewById(R.id.mobile_type);
        }

        public boolean apply(boolean isSecondaryIcon) {
            if (mMobileVisible && !mIsAirplaneMode) {
                mMobile.setImageResource(mMobileStrengthId);
                mMobileType.setImageResource(mMobileTypeId);
                mMobileGroup.setContentDescription(mMobileTypeDescription
                        + " " + mMobileDescription);
                mMobileGroup.setVisibility(View.VISIBLE);
            } else {
                mMobileGroup.setVisibility(View.GONE);
            }

            // When this isn't next to wifi, give it some extra padding between the signals.
            mMobileGroup.setPaddingRelative(isSecondaryIcon ? mSecondaryTelephonyPadding : 0,
                    0, 0, 0);
            mMobile.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0,
                    0, 0, 0);

            if (DEBUG) Log.d(TAG, String.format("mobile: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"), mMobileStrengthId, mMobileTypeId));

            mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);

            return mMobileVisible;
        }

        public void populateAccessibilityEvent(AccessibilityEvent event) {
            if (mMobileVisible && mMobileGroup != null
                    && mMobileGroup.getContentDescription() != null) {
                event.getText().add(mMobileGroup.getContentDescription());
            }
        }
    }

    private int convertMobileStrengthIcon(int icon) {
        int returnVal = icon;
        switch(icon){
            case R.drawable.stat_sys_signal_0_3g:
                returnVal = R.drawable.stat_sys_signal_0_3g_default;
                break;
            case R.drawable.stat_sys_signal_0_4g:
                returnVal = R.drawable.stat_sys_signal_0_4g_default;
                break;
            case R.drawable.stat_sys_signal_1_3g:
                returnVal = R.drawable.stat_sys_signal_1_3g_default;
                break;
            case R.drawable.stat_sys_signal_1_4g:
                returnVal = R.drawable.stat_sys_signal_1_4g_default;
                break;
            case R.drawable.stat_sys_signal_2_3g:
                returnVal = R.drawable.stat_sys_signal_2_3g_default;
                break;
            case R.drawable.stat_sys_signal_2_4g:
                returnVal = R.drawable.stat_sys_signal_2_4g_default;
                break;
            case R.drawable.stat_sys_signal_3_3g:
                returnVal = R.drawable.stat_sys_signal_3_3g_default;
                break;
            case R.drawable.stat_sys_signal_3_4g:
                returnVal = R.drawable.stat_sys_signal_3_4g_default;
                break;
            case R.drawable.stat_sys_signal_4_3g:
                returnVal = R.drawable.stat_sys_signal_4_3g_default;
                break;
            case R.drawable.stat_sys_signal_4_4g:
                returnVal = R.drawable.stat_sys_signal_4_4g_default;
                break;
            case R.drawable.stat_sys_signal_0_3g_fully:
                returnVal = R.drawable.stat_sys_signal_0_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_0_4g_fully:
                returnVal = R.drawable.stat_sys_signal_0_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_1_3g_fully:
                returnVal = R.drawable.stat_sys_signal_1_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_1_4g_fully:
                returnVal = R.drawable.stat_sys_signal_1_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_2_3g_fully:
                returnVal = R.drawable.stat_sys_signal_2_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_2_4g_fully:
                returnVal = R.drawable.stat_sys_signal_2_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_3_3g_fully:
                returnVal = R.drawable.stat_sys_signal_3_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_3_4g_fully:
                returnVal = R.drawable.stat_sys_signal_3_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_4_3g_fully:
                returnVal = R.drawable.stat_sys_signal_4_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_4_4g_fully:
                returnVal = R.drawable.stat_sys_signal_4_4g_default_fully;
                break;
            default:
                break;
        }
        return returnVal;
    }

    private int getCdma2gId(int icon) {
        if (mNC == null) {
            return 0;
        }
        int retValue = 0;
        int level = mNC.getGsmSignalLevel();
        switch(level){
            case SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                retValue = R.drawable.stat_sys_signal_0_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_POOR:
                retValue = R.drawable.stat_sys_signal_1_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_MODERATE:
                retValue = R.drawable.stat_sys_signal_2_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GOOD:
                retValue = R.drawable.stat_sys_signal_3_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GREAT:
                retValue = R.drawable.stat_sys_signal_4_2g;
                break;
            default:
                break;
        }
        return retValue;
    }

    private int getCdmaRoamId(int icon){
        int returnVal = 0;
        switch(icon){
            case R.drawable.stat_sys_signal_0_2g_default_roam:
            case R.drawable.stat_sys_signal_0_3g_default_roam:
            case R.drawable.stat_sys_signal_0_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_0_default_roam;
                break;
            case R.drawable.stat_sys_signal_1_2g_default_roam:
            case R.drawable.stat_sys_signal_1_3g_default_roam:
            case R.drawable.stat_sys_signal_1_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_1_default_roam;
                break;
            case R.drawable.stat_sys_signal_2_2g_default_roam:
            case R.drawable.stat_sys_signal_2_3g_default_roam:
            case R.drawable.stat_sys_signal_2_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_2_default_roam;
                break;
            case R.drawable.stat_sys_signal_3_2g_default_roam:
            case R.drawable.stat_sys_signal_3_3g_default_roam:
            case R.drawable.stat_sys_signal_3_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_3_default_roam;
                break;
            case R.drawable.stat_sys_signal_4_2g_default_roam:
            case R.drawable.stat_sys_signal_4_3g_default_roam:
            case R.drawable.stat_sys_signal_4_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_4_default_roam;
                break;
            case R.drawable.stat_sys_signal_0_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_0_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_0_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_0_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_1_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_1_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_1_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_1_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_2_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_2_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_2_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_2_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_3_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_3_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_3_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_3_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_4_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_4_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_4_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_4_default_fully_roam;
                break;
            default:
                break;
        }
        return returnVal;
    }
}
