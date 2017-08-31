# Troubleshooting


TODO

## `equals()` is not returning what I expect

This is usually a sign that one of your field types is not implementing `equals`
as you expect. A typical offending class is [`JSONObject`]
(https://developer.android.com/reference/org/json/JSONObject.html), which
doesn't override `Object.equals()` and thus compromises your class's `equals`
behavior as well.
