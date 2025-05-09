const functions = require('firebase-functions');
const admin     = require('firebase-admin');
admin.initializeApp();

exports.onNewNotification = functions
  .firestore
  .document('users/{userId}/notifications/{notifId}')
  .onCreate(async (snap, ctx) => {
    const { title, message, type } = snap.data();
    const userId = ctx.params.userId;

    const userDoc = await admin.firestore().doc(`users/${userId}`).get();
    const token   = userDoc.get('fcmToken');
    if (!token) return null;

    return admin.messaging().send({
      token,
      notification: { title, body: message },
      data: { type }
    });
  });
