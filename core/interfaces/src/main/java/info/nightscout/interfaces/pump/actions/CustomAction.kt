package info.nightscout.interfaces.pump.actions

data class CustomAction(
    val name: Int,
    val customActionType: CustomActionType,
    val iconResourceId: Int,
    var isEnabled: Boolean = true
)