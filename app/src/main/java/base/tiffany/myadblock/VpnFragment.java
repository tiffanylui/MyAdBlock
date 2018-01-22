package base.tiffany.myadblock;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;


public class VpnFragment extends Fragment {

    private static final int REQUEST_START_VPN = 1;

    Button mStartVpn;
    Button mStopVpn;

    public VpnFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_OK) {
            Intent intent = new Intent(getContext(), MainVpnService.class);
            getContext().startService(intent);

            mStartVpn.setVisibility(View.INVISIBLE);
            mStopVpn.setVisibility(View.VISIBLE);
        }
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_CANCELED) {
            Toast.makeText(getContext(), "Could not configure VPN service", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_blank, container, false);

        mStartVpn = v.findViewById(R.id.start_vpn);
        mStartVpn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        mStopVpn = v.findViewById(R.id.stop_vpn);
        mStopVpn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnect();
            }
        });


        return v;
    }


    public void connect() {
        startVpn();
    }

    private void startVpn() {
        Intent intent = VpnService.prepare(getActivity());
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN);
        } else {
            onActivityResult(REQUEST_START_VPN, RESULT_OK, null);
        }
    }

    public void disconnect() {
        //Disconect using the Intent
        if (MainVpnService.vpnStatus != MainVpnService.VPN_STATUS_STOPPED) {
            Log.i(getClass().getSimpleName(), "Disconnection");

            Intent intent = new Intent(getActivity(), MainVpnService.class);
            intent.putExtra("STATUS", MainVpnService.STOP);
            getActivity().startService(intent);
            mStopVpn.setVisibility(View.INVISIBLE);
        }
        mStartVpn.setVisibility(View.VISIBLE);

    }
}
