package com.dhananjay.livecast.pairing

import com.google.firebase.Timestamp

internal val firestoreTimestampToMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }
