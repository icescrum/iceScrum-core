package org.icescrum.core.domain


class Mood {
    static final int MOOD_GOOD = 1
    static final int MOOD_AVERAGE = 2
    static final int MOOD_BAD = 3
    int moodUser = Mood.MOOD_GOOD
    static belongsTo = [User]

}


