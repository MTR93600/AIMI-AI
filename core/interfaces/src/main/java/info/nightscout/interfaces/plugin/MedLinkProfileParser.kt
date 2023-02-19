package info.nightscout.interfaces.plugin

import info.nightscout.interfaces.profile.Profile
import java.util.function.Supplier
import java.util.stream.Stream

interface MedLinkProfileParser<T,U> {

    fun parseProfile(answer: Supplier<Stream<String>>, entries: U?): Profile?
}