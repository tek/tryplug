val tv = sys.props.get("tryplug.version")
    .getOrElse(sys.error("need to pass -Dtryplug.version"))
addSbtPlugin("io.tryp" % "tryplug" % tv)
