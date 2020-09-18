package com.example.whist;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GameTab extends Fragment {

    private ArrayList<String> players;
    private int playerCount;
    private int myIndex;

    private DatabaseReference turnReference;
    private DatabaseReference bidReference;
    private DatabaseReference handsReference;

    // lista bid-urilor
    private final ArrayList<Integer> bids = new ArrayList<>();

    private View rootView;
    private Context mContext;

    public GameTab() {}

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            players = getArguments().getStringArrayList("players");
            myIndex = getArguments().getInt("myIndex");
            playerCount = players.size();
        }

        turnReference = FirebaseDatabase.getInstance().getReference().child("Game").child("Turn");
        // creare intrare "Bids" pentru a intreba playerii cate maini vor lua
        bidReference = turnReference.child("Bids");
        // creare intrare "Hands" pentru a gestiona jocurile
        handsReference = turnReference.child("Hands");

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_game_tab, container, false);


        // setare nume playeri
        setPlayerNames(rootView);

        // ascundere avatari nefolositori
        hidePlayerAvatars(rootView);

        // setare listeneri pe butoanele de bid
        setButtonListeners();

        setBidFinishedListener();

        // metoda prin care se ruleaza jocul
        runGame();

        return rootView;
    }

    private void runGame() {
        // (Momentan) se realizeaza un joc de 8
        turn(0, 8);

    }


    public void turn(final int currentPlayerIndex, int gameType) {

        // Cod executat doar de jucatorul care este la rand sa faca bid
        if (currentPlayerIndex + 1 == myIndex) {
            // Amestecare carti + trimitere la server
            ArrayList<String> shuffledCards = CardShuffler.shuffleCards(playerCount);
            Map<String, Object> map = new HashMap<>();

            // Pun pentru fiecare player intr-un map un sublist al listei de carti amestecate
            // (8 carti pentru Player1, urmatoarele 8 carti pentru Player2, etc)
            for (int i = 0; i < playerCount; i++) {
                map.put("Cards", shuffledCards.subList(gameType * i, gameType * (i + 1)));

                // Trimitere la server playeri cu cartile lor amestecate
                DatabaseReference currPlayerReference = turnReference.child("Player" + (i + 1));
                currPlayerReference.updateChildren(map);

                // Trimitere la server Bids
                if(i != currentPlayerIndex) {
                    bidReference.child("Player" + (i + 1)).setValue("Pending");
                    handsReference.child("Player" + (i + 1)).setValue("Pending");
                }
            }

            // setam pe intrarea jucatorului curent faptul ca el este cel care trebuie sa aleaga
            bidReference.child("Player" + (currentPlayerIndex + 1)).setValue("Current");
            handsReference.child("Player" + (currentPlayerIndex + 1)).setValue("Current");
        }

        // Setare listener pe intrarea Player <indicele meu>
        DatabaseReference myPlayerReference = turnReference.child("Player" + myIndex);
        myPlayerReference.addChildEventListener(myPlayerListener());


        /// Inregistrarea bid-urilor jucatorilor
        // setare listener pe bidReference
        bidReference.addChildEventListener(bidListener());
    }



    ////////////////////////////////////////////////////////////////////////////////////////////
                                        // setup


    // metoda care seteaza cate un listener pe fiecare buton de bid
    private void setButtonListeners() {

        GridLayout buttonsGrid = (GridLayout) rootView.findViewById(R.id.buttons_grid);
        final LinearLayout bidLayout = (LinearLayout) rootView.findViewById(R.id.bid_layout);
        int buttonsCount = buttonsGrid.getChildCount();

        for(int i = 0; i < buttonsCount; i++){
            Button b = (Button)buttonsGrid.getChildAt(i);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // extragem valoarea in functie de butonul apasat
                    // adaugam valoarea la lista bids
                    Button b = (Button) view;
                    Integer value = Integer.parseInt(b.getText().toString());

                    // ascundem bid_layout
                    bidLayout.setVisibility(View.GONE);

                    // setam bid-ul in baza de date
                    bidReference.child("Player" + myIndex).setValue(value);

                    // setam valoarea "Current" in baza de date pentru persoana urmatoare
                    // daca toata lumea a pariat, setam bid = true pentru a trece la partea de dat carti
                    if(myIndex < playerCount) {
                        bidReference.child("Player" + (myIndex + 1)).setValue("Current");

                    } else if(myIndex == playerCount){
                        // aici: se executa codul de dupa partea de bid
                        turnReference.child("BidFinished").child("IsFinished").setValue("True");
                    }
                }
            });
        }
    }

    // metoda care extrage textview-urile din fragment_game_tab si seteaza numele jucatorilor
    private void setPlayerNames(View rootView) {

        int index = 0;

        ArrayList<TextView> playerTextView = new ArrayList<>(5);
        playerTextView.add((TextView) rootView.findViewById(R.id.player1_name));
        playerTextView.add((TextView) rootView.findViewById(R.id.player2_name));
        playerTextView.add((TextView) rootView.findViewById(R.id.player3_name));
        playerTextView.add((TextView) rootView.findViewById(R.id.player4_name));
        playerTextView.add((TextView) rootView.findViewById(R.id.player5_name));

        for (int i = 0; i < players.size(); i++) {
            if ((i + 1) != myIndex) {
                playerTextView.get(index++).setText(players.get(i));
            }
        }
    }

    // metoda care extrage layout-urile din fragment_game_tab si le ascunde pe cele nefolositoare
    private void hidePlayerAvatars(View rootView) {

        ArrayList<LinearLayout> playerLinearLayout = new ArrayList<>(5);
        playerLinearLayout.add((LinearLayout) rootView.findViewById(R.id.opponent_layout1));
        playerLinearLayout.add((LinearLayout) rootView.findViewById(R.id.opponent_layout2));
        playerLinearLayout.add((LinearLayout) rootView.findViewById(R.id.opponent_layout3));
        playerLinearLayout.add((LinearLayout) rootView.findViewById(R.id.opponent_layout4));
        playerLinearLayout.add((LinearLayout) rootView.findViewById(R.id.opponent_layout5));


        switch (playerCount) {
            case 3:
                playerLinearLayout.get(2).setVisibility(View.GONE);
                playerLinearLayout.get(3).setVisibility(View.GONE);
                playerLinearLayout.get(4).setVisibility(View.GONE);
                break;
            case 4:
                playerLinearLayout.get(3).setVisibility(View.GONE);
                playerLinearLayout.get(4).setVisibility(View.GONE);
                break;
            case 5:
                playerLinearLayout.get(4).setVisibility(View.GONE);
                break;
        }
    }

    // metoda care seteaza pe ecran bid-urile date de fiecare jucator
    private void setBidTextViews() {
        // ArrayList cu textView-urile in care se pun bid-urile
        ArrayList<TextView> bidTextViews = new ArrayList<>(5);
        bidTextViews.add((TextView) rootView.findViewById(R.id.player1_bid));
        bidTextViews.add((TextView) rootView.findViewById(R.id.player2_bid));
        bidTextViews.add((TextView) rootView.findViewById(R.id.player3_bid));
        bidTextViews.add((TextView) rootView.findViewById(R.id.player4_bid));
        bidTextViews.add((TextView) rootView.findViewById(R.id.player5_bid));

        // textview-ul in care se pune bid-ul jucatorului curent
        TextView myBidTextView = rootView.findViewById(R.id.my_bid_text_view);
        myBidTextView.setVisibility(View.VISIBLE);
        myBidTextView.setText("Bid: " + bids.get(myIndex - 1));

        int index = 0;
        // setare bid pt adversari
        for (int i = 0; i < players.size(); i++) {
            if ((i + 1) != myIndex) {
                bidTextViews.get(index).setVisibility(View.VISIBLE);
                bidTextViews.get(index++).setText("Bid: " + bids.get(i));
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
                                        // Listeneri

    // listener pe intrarea "Player"
    private ChildEventListener myPlayerListener() {
        return new ChildEventListener() {

            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Extragere carti de la intrarea Player cu indicele jucatorului curent
                GenericTypeIndicator<ArrayList<String>> t = new GenericTypeIndicator<ArrayList<String>>() {
                };
                ArrayList<String> myCards = snapshot.getValue(t);

                // Introducere carti pe slot-urile libere din fragment_game_tab.xml
                for (int i = 0; i < myCards.size(); i++) {
                    // extragere id al slot-ului
                    int resId = mContext.getResources().getIdentifier(
                            "card_slot_" + (i + 1),
                            "id",
                            mContext.getPackageName()
                    );
                    // extragere id al slot-ului
                    int drawableId = mContext.getResources().getIdentifier(
                            myCards.get(i),
                            "drawable",
                            mContext.getPackageName()
                    );

                    ImageView card = rootView.findViewById(resId);
                    // setare resursa pe slot
                    card.setImageResource(drawableId);
                    card.setContentDescription(myCards.get(i));
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
    }


    // listener pe intrarea de Bid
    private ChildEventListener bidListener() {
        return new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String key = snapshot.getKey();
                String result = snapshot.getValue(String.class);

                if(key.equals("Player" + (myIndex)) && result.equals("Current")) {

                    // setam vizibilitatea layout-ului din mijloc pe true
                    LinearLayout bidLayout = (LinearLayout) rootView.findViewById(R.id.bid_layout);
                    bidLayout.setVisibility(View.VISIBLE);

                } else {
                    // adaugam in arraylist valorile numerice
                    if(result.equals("Current") == false && result.equals("Pending") == false) {
                        bids.add(Integer.parseInt(result));
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String key = snapshot.getKey();
                String result = snapshot.getValue().toString();

                // jucatorul curent trebuie sa faca bid
                // adaugam pe ecran partea de bid, intrebam utilizatorul cate maini ia, trimitem rezultatul
                // la server, ascundem partea de bid, setam pt
                // urmatorul player Current (daca nu suntem ultimii)
                if(key.equals("Player" + (myIndex)) && result.equals("Current")) {

                    // setam vizibilitatea layout-ului din mijloc pe true
                    LinearLayout bidLayout = (LinearLayout) rootView.findViewById(R.id.bid_layout);
                    bidLayout.setVisibility(View.VISIBLE);

                } else {
                    // adaugam in arraylist valorile numerice
                    if(result.equals("Current") == false && result.equals("Pending") == false) {
                        bids.add(Integer.parseInt(result));
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
    }

    // listener pentru cand partea de bid s-a terminat
    // declanseaza partea de dat carti a jocului
    private void setBidFinishedListener() {
        turnReference.child("BidFinished").child("IsFinished").setValue("False");

        turnReference.child("BidFinished").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String value = snapshot.getValue(String.class);
                // cand intrarea BidFinished are valoarea true, afisam bid-urile fiecarui jucator si
                // setam listener-ul de pe intrarea hands
                if(value.equals("True")) {
                    setBidTextViews();
                    handsReference.addChildEventListener(handsListener());
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String value = snapshot.getValue(String.class);
                // cand intrarea BidFinished are valoarea true, afisam bid-urile fiecarui jucator si
                // setam listener-ul de pe intrarea hands
                if(value.equals("True")) {
                    setBidTextViews();
                    handsReference.addChildEventListener(handsListener());
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // listener pe intrarea hands
    private ChildEventListener handsListener() {
        return new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // setare onClick pe carti atunci cand este randul jucatorului curent
                String key = snapshot.getKey();
                String value = snapshot.getValue(String.class);

                if (key.equals("Player" + myIndex) && value.equals("Current")) {
                    setCardOnClick();
                }

            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // setare onClick pe carti atunci cand este randul jucatorului curent
                String key = snapshot.getKey();
                String value = snapshot.getValue(String.class);

                if (key.equals("Player" + myIndex) && value.equals("Current")) {
                    setCardOnClick();
                }

                // daca alt jucator a dat o carte, afisam cartea in dreptul sau
                if(key.equals("Player" + myIndex) == false && value.equals("Current") == false) {

                    // extragem indicele
                    int playerIndex = Character.getNumericValue(key.charAt(key.length() - 1));

                    // ajustam indicele (pentru a pune cartea in slot-ul corect)
                    if(playerIndex > myIndex) {
                        playerIndex--;
                    }

                    // extragem id-ul slotului in care punem imaginea
                    int resId = mContext.getResources().getIdentifier(
                            "slot_player" + playerIndex,
                            "id",
                            mContext.getPackageName()
                    );

                    // extragem drawable
                    int drawableId = mContext.getResources().getIdentifier(
                            value,
                            "drawable",
                            mContext.getPackageName()
                    );

                    ImageView card = rootView.findViewById(resId);
                    // setare resursa pe slot
                    card.setImageResource(drawableId);
                    card.setVisibility(View.VISIBLE);
                    card.setContentDescription(value);
                }
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
    }

    // mmetoda care seteaza onClick pe carti
    private void setCardOnClick() {

        for(int i = 0; i < 8; i++) {
            // extragere id al cartii in functie de nume
            int resId = mContext.getResources().getIdentifier(
                    "card_slot_" + (i + 1),
                    "id",
                    mContext.getPackageName()
            );

            // setare listener pe carte
            ImageView img = (ImageView) rootView.findViewById(resId);
            img.setOnClickListener(cardOnClickListener());
        }
    }

    // onClick pentru o carte
    private View.OnClickListener cardOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageView imageView = (ImageView) view;
                // setare vizibilitate pe gone
                imageView.setVisibility(View.GONE);
                // extragere nume carte din descrierea ei
                String cardName = imageView.getContentDescription().toString();
                // se trimite la baza de date numele cartii care a fost data
                handsReference.child("Player" + myIndex).setValue(cardName);
                // se sterge descrierea de pe carte
                imageView.setContentDescription(null);
                // cu exceptia cazului in care jucatorul este ultimul, se seteaza "Current" pe intrarea
                // urmatorului jucator pentru a-l anunta ca el trebuie sa dea carte
                if (myIndex != playerCount) {
                    handsReference.child("Player" + (myIndex + 1)).setValue("Current");
                }
                // se elimina metodele de onClick de pe imagini
                for(int i = 0; i < 8; i++) {
                    int resId = mContext.getResources().getIdentifier(
                            "card_slot_" + (i + 1),
                            "id",
                            mContext.getPackageName()
                    );
                    ImageView img = (ImageView) rootView.findViewById(resId);
                    img.setOnClickListener(null);
                }
                // setare imagine cu cartea data pe slotul jucatorului curent
                ImageView newImg = (ImageView) rootView.findViewById(R.id.my_card_slot);
                newImg.setVisibility(View.VISIBLE);
                newImg.setImageDrawable(imageView.getDrawable());
            }
        };
    }
}