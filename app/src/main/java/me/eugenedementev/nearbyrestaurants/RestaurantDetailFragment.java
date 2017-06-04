package me.eugenedementev.nearbyrestaurants;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A placeholder fragment containing a simple view.
 */
public class RestaurantDetailFragment extends Fragment {

    public RestaurantDetailFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_restaurant_detail, container, false);

        //Get restaurant object from intent
        String restaurantString = getActivity().getIntent().getStringExtra(MainActivity.RESTAURANT_INTENT_TAG);
        if (restaurantString == null){
            return view;
        }
        try {
            ImageView restaurantImage = (ImageView) view.findViewById(R.id.restaurantImage);
            TextView phoneNumberTextView = (TextView) view.findViewById(R.id.phoneNumberTextView);
            TextView categoryTextView = (TextView) view.findViewById(R.id.categoryTextView);
            TextView ratingTextView = (TextView) view.findViewById(R.id.ratingTextView);

            JSONObject restaurant = new JSONObject(restaurantString);

            Picasso.with(getActivity()).load(restaurant.getString("image_url")).into(restaurantImage);
            getActivity().setTitle(restaurant.getString("name"));
            phoneNumberTextView.setText(restaurant.getString("phone"));
            ratingTextView.setText(restaurant.getString("rating"));

            JSONArray categories = restaurant.getJSONArray("categories");
            categoryTextView.setText("");
            for (int i=0; i < categories.length();i++){
                categoryTextView.setText(
                        categories.getJSONObject(i).getString("title")
                        + ", "
                );
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return view;
    }
}
