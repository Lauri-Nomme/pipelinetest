pipeline {
    agent any
    tools {
            maven 'Maven 3.3.9'
            ant 'Default'
    }
    stages {
        stage ('Build') {
            steps {
                sh 'mvn -T 1C verify package findbugs:check'
                step([$class: 'FindBugsPublisher', pattern: '**/findbugsXml.xml'])
            }
        }
    }

    post {
        always {
            junit '**/target/surefire-reports/*.xml'
            archiveArtifacts artifacts: '*/*', fingerprint: true
            script {
                currentBuild.result = currentBuild.result ?: currentBuild.currentResult
            }
        }
    }
}
