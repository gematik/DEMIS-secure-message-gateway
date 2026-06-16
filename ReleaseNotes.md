<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>    

# Release Notes Secure-Message-Gateway

## Release 1.0.0 
- updated base-image and updated from java 21 to java 25
- Removed istio helm chart
- Initial version
- Implemented rest interface for receiving messages (notification, error, and status)
- Removed queue configurations
- message encryption
- Adapted to new HTTP headers for routing
- Upgraded to spring boot 4
- Added secret mapping for seperate rabbitmq user, password and vhost
- wait for message broker confirmation
- Added observation for rabbit template
