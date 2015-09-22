import hudson.security.*
import jenkins.security.*

//
// USE ONLY ON LOCAL JENKINS FOR TESTS !!!
// DELETES ALL USERS FROM JENKINS !!!!
//

for ( userInfo in Jenkins.instance.getPeople().users ) {
    User user = userInfo.getUser()
    print("Deleting user "+user)
    user.delete()
}

for ( userInfo in Jenkins.instance.getPeople().users ) {
    println(userInfo.getUser()+"\n")
}