properties([
    pipelineTriggers([
        pollSCM('')
    ]),
    buildDiscarder(
        logRotator(
            daysToKeepStr: '20',
            artifactDaysToKeepStr: '60'
        )
    ),
    disableConcurrentBuilds()
])

timeout(45) {
    String repositoryName = "asup-adrsynchronizer-service"
    String podName = repositoryName.take(50)
    String nodeLabel = "n" + env.BUILD_TAG.reverse().take(62).reverse().replaceAll('%2F', '-').replaceAll(/^\-/, '')
    String branchName = env.BRANCH_NAME
    String buildNumber = env.BUILD_NUMBER 
	gitCommitHash = "unknown"
    gitCommitDate = "unknown"	
    branchName = branchName.reverse().take(62).reverse().replaceAll('/', '-')
	String imageName = "fantito/jdk11-maven-git"

    podTemplate(
         label: nodeLabel,
            volumes: [
                    secretVolume(
                            secretName: 'dockerhost-secret-files',
                            // A docker login will store auth tokens at ~/.docker/config.json
                            // If this is a secretVolume, kubernetes may update the volume to the state it knows of
                            // That means config.json may get lost
                            // Mount to another path instead and expose using DOCKER_CERT_PATH
                            mountPath: '/home/jenkins/.dockercert'
                    )
            ],
       containers: [
                    containerTemplate(
                            name: 'jnlp',
                            image: imageName,
                            alwaysPullImage: true,
                            args: '${computer.jnlpmac} ${computer.name}',
                            envVars: [
                                    envVar(key: "DOCKER_CERT_PATH", value: "/home/jenkins/.dockercert"),
                                    envVar(key: "DOCKER_TLS_VERIFY", value: "${env.SHARED_DOCKER_TLS_VERIFY}"),
                                    envVar(key: "DOCKER_HOST", value: "${env.SHARED_DOCKER_HOST}"),
                                    envVar(key: "DOCKER_API_VERSION", value: "${env.SHARED_DOCKER_API_VERSION}")
                            ]
                    )
            ]
    ) {
        node(nodeLabel)
        {
            try {
                stage("Clone repo") {
                    gitCredentialsId = "crtx-creds-id"
                    gitRepoUrl = "https://github.com/singhdilraj1988/com.example.demo.git"
                    gitBranchName = env.BRANCH_NAME
                    gitRefs = cloneRepository {
                        gitRepoUrl       = this.gitRepoUrl
                        gitCredentialsId = this.gitCredentialsId
                        gitBranchName    = this.gitBranchName
                    }
					gitCommitHash = sh (script: "git reflog show origin/${env.BRANCH_NAME} --pretty=\'%h\' -n 1",returnStdout: true).trim()
                    gitCommitDate = sh (script: "git reflog show origin/${env.BRANCH_NAME} --pretty=\'%gd\' --date=format:%Y%m%d%H%M -n 1",returnStdout: true).trim().replace("origin/${env.BRANCH_NAME}@{",'').replace("}",'')
                } // end stage Clone repo
        	    stage("Run Code Checkstyle") {
                        sh"""
                            mvn checkstyle:checkstyle
                        """        	      
                } //end stage Run Code Checkstyle               
                stage("Build & Test Project") {                
                        sh """
                             mvn install                        
                        """                 
                } // end stage Build & Test Project
                stage("Build and Push Docker Image") {                           
                           version = readFile("${env.WORKSPACE}/VERSION").split('"')[3]
                           tag = version + "-" + this.gitCommitDate + "-" + this.gitCommitHash + "-" + env.BUILD_NUMBER
                           echo " docker tag is set : $tag"
                           docker.withRegistry("https://artifactory-pyml-auto-images-public.cto.veritas.com", "crtx-creds-id-token") {
                           def dockerfile = 'cicd/docker/Dockerfile'
                           def customImage = docker.build("singhdilraj1988/com.example.demo:$tag","-f ${dockerfile} .")
                           customImage.push()
                           sh "docker rmi singhdilraj1988/com.example.demo:$tag"
                        }
                    } //end stage                
            } //end try
            catch(IllegalStateException ex) {
                // This is thrown from cloneRepository step in case of intermediate commit build
                currentBuild.result = currentBuild.result ?: "NOT_BUILT"
                println ex.getMessage()
            }
            catch(Exception e) {
                // If there was an exception thrown, the build failed
                currentBuild.result = "FAILURE"
                println e.getMessage()
                throw e
            }
            finally {
                stage("Run post-build tasks") {                   
                    
                } // end stage Run post-build tasks
            } // end finally
        } // end node
    } // end podTemplate
} // end timeout