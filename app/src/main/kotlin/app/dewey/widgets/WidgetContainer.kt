package app.dewey.widgets

import android.content.Context
import app.dewey.DeweyApplication
import app.dewey.di.AppContainer

/**
 * The app's dependency graph, reached the same way every screen reaches it -
 * see AppContainer's own doc. Widgets only ever get a bare [Context] from
 * Glance, so this is the one cast every widget's `provideGlance` needs.
 */
internal fun Context.deweyContainer(): AppContainer = (applicationContext as DeweyApplication).container
