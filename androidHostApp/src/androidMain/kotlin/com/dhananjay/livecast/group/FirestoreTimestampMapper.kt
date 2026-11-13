package com.dhananjay.livecast.group

import com.google.firebase.Timestamp

internal val firestoreTimestampToMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }
