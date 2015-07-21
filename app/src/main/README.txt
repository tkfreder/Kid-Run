Kid Run  (kid-version of Run Keeper)
Tracks distance, time, speed traveled.  User earns virtual coins for distance traveled.  
Coins are totalled and can be redeemed for activities or privileges.  Fulfillment tracking feature not implemented yet.
The application uses Fused Position API to get location data every 1-3 seconds, 
but only saves the location if it meets the accuracy setting.  This is currently set at 10 meters.  
You may need to go outside for a better connection to get the accuracy high enough.  
The logcat debug window should be displaying the accuracy values.
The user earns 1 coin per 2 feet (normally this would be higher, but it's low for testing purposes)
The Service will run 30 minutes before it automatically shuts itself off.  
The Service notifies MainActivity every 10 seconds or so (depending on accuracy) to fetch new data and update the UI.

The use case scenario is as follows:

1.  Launch app.
2.  Click on "Go!" button
3.  Walk from one end of the house to the other.
4.  Timer will start.  
5.  Depending on the accuracy of the location data, it should save your location periodically and calculate the distance travelled in feet.
6.  The "feet" and "coin" counter should be updating.  You should earn 1 coin per 2 feet travelled.
7.  Each time the display is updated and you have earned additional coins, the app will play a sound.
8.  Click on "Stop".  The app will display the total coins earned and play a sound.  (And you can stop walking or not).
9.  Click on "Done" will take you to the "Rewards" screen, which displays the what you can redeem with the coins earned.

I would like to have done more with this app, but this is what I could do with the time I had.  
I already see a practical and fun use for my family.  Most likely I will work on this further and publish it to Google Play.




