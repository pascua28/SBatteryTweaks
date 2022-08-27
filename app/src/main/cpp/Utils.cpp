//
// Created by Samuel Pascua on 8/22/2022.
//

#include <iostream>
#include <fstream>
#include <jni.h>
#include <sstream>
#include <strstream>
#include <regex>
#include <android/log.h>

using std::string;
using std::ifstream;

string convertToString(char* a, int size)
{
    int i;
    string s = "";
    for (i = 0; i < size; i++) {
        s = s + a[i];
    }
    return s;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sammy_chargerateautomator_Utils_readFile(JNIEnv *env, jobject thiz, jstring file_path) {
    string filePath = (*env).GetStringUTFChars(file_path, JNI_FALSE);
    char line[4];

    FILE *fp = fopen(filePath.c_str(), "r");

    if (fp == NULL)
        return env->NewStringUTF("0");
    
    fgets(line, 4, fp);
    fclose(fp);

    string ret(line);
    ret.erase(std::remove(ret.begin(), ret.end(), '\n'), ret.cend());

    return env->NewStringUTF(ret.c_str());
}